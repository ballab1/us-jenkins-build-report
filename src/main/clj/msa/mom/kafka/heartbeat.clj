(ns msa.mom.kafka.heartbeat
  "heartbeat core"
  (:require [clojure.core.async :as async]
            [config.env :as cfg]
            [msa.mom.kafka.consumer :refer [consumer-base-cfg]]
            [msa.mom.kafka.producer :refer [create-msg-producer
                                            define-producer]]
            [taoensso.timbre :as log])
  (:import (com.fasterxml.uuid EthernetAddress Generators))
  (:gen-class))

(defrecord HeartbeatMessage
           [^String uuid
            ^long sequence_id
            ^String timestamp
            ^String hostname
            ^java.util.Collection host_ips
            ^String kafka_client_id
            ^String kafka_group_id
            ^java.util.Collection topics
            ^long next_heartbeat_in_ms])

(def UUIDv1 (Generators/timeBasedGenerator (EthernetAddress/fromInterface)))

(defn local-addresses []
  (->> (java.net.NetworkInterface/getNetworkInterfaces)
       enumeration-seq
       (map bean)
       (filter (complement :loopback))
       (mapcat :interfaceAddresses)
       (map #(.. % (getAddress) (getHostAddress)))))

(defn create-hb-msg
  "Default 'constructor' for a heartbeat message"
  [& [heartbeat-interval-ms n]]
  ; record instance
  (HeartbeatMessage.
   (str (.generate UUIDv1))
   (long (or n 0))
   (str (java.time.Instant/now))
   (str (java.net.InetAddress/getLocalHost))
   (local-addresses)
   (cfg/client-id)
   (cfg/group-id)
   [(cfg/topic) (cfg/hb-topic)]
   (long (or heartbeat-interval-ms 120000)))
  ; map instance
  #_{:uuid (str (.generate UUIDv1))
   :sequence_id (or  n 0)
   :timestamp (str (java.time.Instant/now))
   :hostname (str (java.net.InetAddress/getLocalHost))
   :host_ips (local-addresses)
   :kafka_client_id (cfg/client-id)
   :kafka_group_id (cfg/group-id)
   :topics [(cfg/topic) (cfh/hb-topic)]
   :next_heartbeat_in_ms (or heartbeat-interval-ms 120000)})

(defn update-hb-msg [hb-msg n]
  ; Records are immutable, assoc returns a new instance with updated fields.
  (assoc hb-msg
         :uuid (str (.generate UUIDv1))
         :sequence_id (long  n)
         :timestamp (str (java.time.Instant/now))))

(defn publish-heartbeat [chan]
  (let [publish-hb-msg (create-msg-producer (define-producer :json (cfg/hb-topic)))]
    (async/go-loop [n 1]
      (let [hb-msg (async/<! chan)]
        (when hb-msg
          (publish-hb-msg hb-msg)
          (recur (inc n)))))
    (log/info "Publishing heartbeats...")))

(defn generate-heartbeat [chan heartbeat-message heartbeat-interval-ms]
  (async/go-loop [n 1]
    (async/>! chan (update-hb-msg heartbeat-message n))
    (async/<! (async/timeout heartbeat-interval-ms))
    (recur (inc n)))
  (log/info "Generating heartbeats..."))

(defn start-heartbeat [heartbeat-interval-ms]
  (let [heartbeat-message (create-hb-msg heartbeat-interval-ms)
        chan (async/chan)]
    (generate-heartbeat chan heartbeat-message heartbeat-interval-ms)
    (publish-heartbeat chan)))
