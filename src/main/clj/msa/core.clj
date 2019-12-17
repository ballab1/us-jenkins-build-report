(ns msa.core
  "Application core"
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [environ.core :refer [env]]
            [hugsql.core :as hugsql]
            [mount.core :as mount]
            [msa.db.pg :as pg]
            [msa.mom.kafka.consumer :refer [consume-messages
                                            create-batch-consumer
                                            assign-consumer-topic
                                            subscribe-consumer-topic]]
            [msa.mom.kafka.heartbeat :refer [start-heartbeat]]
            [msa.mom.kafka.producer :refer [define-producer get-dlq-producer]]
            [specs.common :as sc]
            [taoensso.timbre :as log])
  (:import (com.fasterxml.uuid EthernetAddress Generators)
           (org.postgresql.util PGobject))
  (:gen-class))

(s/def ::absolute_url string?)
(s/def ::build_cause string?)
(s/def ::build_number string?)
(s/def ::display_name string?)
(s/def ::duration string?)
(s/def ::duration_string string?)
(s/def ::id string?)
(s/def ::project_name string?)
(s/def ::result string?)
(s/def ::timestamp string?)
(s/def ::jenkins_build_report-msg
  (s/keys :req-un [::absolute_url
                   ::build_cause
                   ::build_number
                   ::display_name
                   ::duration
                   ::duration_string
                   ::id
                   ::project_name
                   ::result
                   ::timestamp]))

; For debugging SQL
(hugsql/def-sqlvec-fns "jenkins_build_report.sql")

(def dlq-producer-def (define-producer :json
                        (or (env :dlq-topic) "docker-cleanup-dlq")))

(hugsql/def-db-fns "jenkins_build_report.sql")

(def UUIDv1 (Generators/timeBasedGenerator (EthernetAddress/fromInterface)))

; In case of "mount.core.DerefableState cannot be cast to ..." errors see:
; https://github.com/yogthos/clojure-error-message-catalog under lib/mount.
(defn mount
  "Mount system resources"
  [] (mount/start))

(defn unmount
  "Unmount system resources"
  [] (mount/stop))

(defn msg->columns->db!
  "Extract keys from message msg-map to form a row of data to be inserted into the DB"
  [msg-map kafka-client-id kafka-group-id]
  (if (s/valid? ::jenkins_build_report-msg msg-map)
    (let [record-id (.generate UUIDv1)
          {:keys [absolute_url build_cause build_number display_name duration duration_string id project_name result timestamp]} msg-map
          deleted_items (PGobject.)
          orig-json (PGobject.)]
                 
      ; Help the JDBC driver out
      (doto deleted_items (.setType "jsonb")(.setValue(json/write-str(:deleted_items msg-map) )))
      (doto orig-json (.setType "jsonb") (.setValue (:orig-json msg-map)))

      (log/debug "Processing msg->columns->db!:"
                 (reduce #(str %1 ", " '%2 "='" %2 "'") "Let values"
                         [record-id hostname docker_percentage_used docker_space_used_in_GB timestamp])
                 (.getValue deleted_items)
                 (.getValue orig-json))
      (jdbc/with-db-connection [conn pg/sink-db]
        (jdbc/insert! conn :kafka.jenkins_build_report nil
                      [record-id
                       absolute_url
                       build_cause
                       (Long/parseLong build_number)
                       display_name
                       (Long/parseLong duration)
                       duration_string
                       id
                       project_name
                       result
                       (java.sql.Timestamp/from (java.time.Instant/parse timestamp)))
                       deleted_items
                       orig-json])))
    (let [publish-dlq-msg (get-dlq-producer ::jenkins_build_report-msg dlq-producer-def)]
      (publish-dlq-msg {:orig-json (:orig-json msg-map)}))))

; kafka-topics.sh --create --topic jenkins_build_report --partitions 1 --replication-factor 1 --zookeeper localhost
(defn -main
  "Entry point of the consuming micro service"
  []
  ; Mount the database
  (mount)

  ; Create the schema and table if needed
  (log/debug "here")
  (when-not (:exists (first (jenkins_build_report-table-exists? pg/sink-db)))
    (create-jenkins_build_report-table pg/sink-db))
  ; Emit a heartbeat message every 2 minutes
  (start-heartbeat 120000)

  ; Start processing messages
  (let [batch-consumer (create-batch-consumer) ; Always commits regardless of auto commit setting.
        topic (or (env :topic) "jenkins_build_report")]
    ;(assign-consumer-topic batch-consumer topic 0 0)
    (subscribe-consumer-topic batch-consumer topic)
    (consume-messages batch-consumer
                      (if (env :run-dev-mode) #'msg->columns->db! msg->columns->db!)
                      :json)))
