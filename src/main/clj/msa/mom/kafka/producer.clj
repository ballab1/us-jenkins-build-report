;; https://techblog.roomkey.com/index.html
;; http://www.confluent.io/blog/tutorial-getting-started-with-the-new-apache-kafka-0.9-consumer-client
(ns msa.mom.kafka.producer
  (:require [cheshire.core :as json]
            [clojure.spec.alpha :as s]
            [cognitect.transit :as transit]
            [config.env :as cfg]
            [msgpack.core :as msg]
            [taoensso.nippy :as nippy]
            [taoensso.timbre :as log]
            ; Merely including will initialize bunyan logging
            [timbre.appenders.bunyan])
  (:import (java.io ByteArrayOutputStream)
           (java.net InetAddress)
           (java.nio.charset StandardCharsets)
           (java.time Instant)
           (org.apache.kafka.clients.producer KafkaProducer ProducerRecord)
           (org.apache.kafka.common.serialization ByteArraySerializer))
  (:gen-class))

;(def bootstrap-servers (or (System/getenv "BOOTSTRAP_SERVERS") "localhost:9092"))
(def kafka-dev-mode (or (System/getenv "KAFKA_DEV_MODE") false))

; Default producer configuration for development/testing. Some env. variables can override.
(def producer-base-cfg
  {"bootstrap.servers" (cfg/bootstrap-servers)
   ;"client.id" (str (.getHostName (InetAddress/getLocalHost)) ":" (cfg/client-id) ":prod")
   "client.id" (cfg/client-id)
   ;"api.version.request" true
   "key.serializer" ByteArraySerializer
   "value.serializer" ByteArraySerializer})

(when (cfg/run-dev-mode)
  (log/debug "Producer base configuration =>" producer-base-cfg))

(defmulti send-msg
  "Producer functions to serialize a message and send to topic. All assume a
  map as message input. The message will be automatically enriched with the
  event time."
  (fn [^KafkaProducer producer pd-map m]
    (:msg-type pd-map)))

(defmethod send-msg :json
  [^KafkaProducer producer pd-map m]
  (let [topic (:topic-name pd-map)
        enr-m (assoc m :serialize_ts (str (Instant/now)))]
    (log/debug "Sending message serialized in json to" topic enr-m)
    (.send producer (ProducerRecord.
                     topic
                     ; Convert String to ByteArray
                     (.getBytes (json/generate-string enr-m) StandardCharsets/UTF_8)))))

(defmethod send-msg :nippy
  [^KafkaProducer producer pd-map m]
  (let [topic (:topic-name pd-map)
        enr-m (assoc m :serialization "nippy" :serialize-ts (java.util.Date.))]
    (log/debug "Sending message serialized in nippy to" topic enr-m)
    (.send producer (ProducerRecord. topic (nippy/freeze enr-m)))))

(defmethod send-msg :msgpack
  [^KafkaProducer producer pd-map m]
  (let [topic (:topic-name pd-map)
        enr-m (assoc m :serialization "msg-pack" :serialize-ts (str (Instant/now)))]
    (log/debug "Sending message serialized in msgpack to" topic m)
    (.send producer (ProducerRecord. topic (msg/pack enr-m)))))

(defmethod send-msg :transit
  [^KafkaProducer producer pd-map m]
  ; See https://github.com/cognitect/transit-clj for details
  (let [out (ByteArrayOutputStream. 4096)
        transit-type (:transit-type pd-map)
        writer (transit/writer out transit-type)
        topic (:topic-name pd-map)
        enr-m (assoc m
                     :serialization (str "transit-" (name transit-type))
                     :serialize-ts (java.util.Date.))]
    (log/debug "Sending message serialized in transit" (name transit-type) "to" topic enr-m)
    (transit/write writer enr-m)
    (.send producer (ProducerRecord. topic (.toByteArray out)))))

(defn define-producer
  "Return a producer definition map setting a canonical client.id and other relevant
  configuration parameters like topic and message type."
  [msg-type topic-name & [options-map]]
  (merge {:kafka-prod-cfg producer-base-cfg
          :topic-name topic-name
          :msg-type msg-type} (or options-map {})))

(defn create-msg-producer
  "Create a closure returning a message publishing function with a KafkaProducer
  bound and configured based on producer definition map pd-map."
  [pd-map]
  (let [kafka-prod-cfg (:kafka-prod-cfg pd-map)
        producer (KafkaProducer. kafka-prod-cfg)]
    (log/info "Producer config for"
              (:topic-name pd-map)
              "=>" kafka-prod-cfg)
    (fn [msg]
      (send-msg producer pd-map msg))))

(def get-msg-producer
  "Returns a memoized function of create-msg-producer which in turn returns a
  closure with a closed KafkaProducer."
  (memoize create-msg-producer))

(defn create-dlq-producer
  "Return a function that logs an invalid message error and publishes a message to a dead letter
  queue given a message spec and a producer definition map"
  [spec dlq-prod-map]
  (let [publish-dlq-msg (create-msg-producer dlq-prod-map)]
    (fn [msg-map]
      (let [spec-explanation (with-out-str (s/explain spec msg-map))]
        (log/error
         "Skipped message does not conform to spec:" msg-map
         "SPEC EXPLANATION:" spec-explanation)
        (publish-dlq-msg
         (assoc msg-map :spec-violation-explanation
                (clojure.string/trim-newline spec-explanation)))))))

(def get-dlq-producer
  "Returns a memoized function of create-dlq-producer which in turn returns a
  closure with a closed KafkaProducer."
  (memoize create-dlq-producer))

(comment
  (require '(clojure [pprint :as cp]) ; cp/pprint
           '(puget [printer :as pp])) ; pp/cprint

  (defn gen-msg-map []
    {:record-id  (str (.generate (com.fasterxml.uuid.Generators/timeBasedGenerator
                                  (com.fasterxml.uuid.EthernetAddress/fromInterface))))
     :size (str (Math/abs (.nextLong (java.util.Random.))))
     :event-ts-str (str (java.time.Instant/now))
     :event-time-ms-epoch (str (System/currentTimeMillis))})

  (defn gen-json-msg []
    (assoc (gen-msg-map) :event-ts (str (java.time.Instant/now))))

  (puget.printer/cprint (gen-msg-map))
  (puget.printer/cprint (gen-json-msg))

  (def publish-json-msg (create-msg-producer (define-producer :json
                                               (str (cfg/topic) "-test-json"))))
  (publish-json-msg (gen-json-msg))
  (dotimes [n 3] (publish-json-msg (gen-json-msg)) (Thread/sleep 200))
  (def f (future (dotimes [n 300] (publish-json-msg (gen-json-msg)) (Thread/sleep 1000))))

  (def publish-transitj-msg (create-msg-producer
                             (define-producer :transit (str (cfg/topic) "-test-transit-json")
                               {:transit-type :json})))
  (publish-transitj-msg (assoc (gen-msg-map) :event-ts (java.util.Date.)))

  (def publish-msgpack-msg (create-msg-producer
                            (define-producer :msgpack (str (cfg/topic) "-test-msgpack"))))
  (publish-msgpack-msg (assoc (gen-msg-map) :event-ts (str (java.time.Instant/now))))

  (def publish-nippy-msg (create-msg-producer
                          (define-producer :nippy (str (cfg/topic) "-test-nippy"))))
  (publish-nippy-msg (assoc (gen-msg-map) :event-ts (java.util.Date.))))
