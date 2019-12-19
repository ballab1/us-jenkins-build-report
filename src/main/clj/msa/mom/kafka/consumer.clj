;; https://techblog.roomkey.com/index.html
;; http://www.confluent.io/blog/tutorial-getting-started-with-the-new-apache-kafka-0.9-consumer-client
(ns msa.mom.kafka.consumer
  {:clj-kondo/config
   '{:linters {:unresolved-symbol {:exclude [(msa.mom.kafka.consumer [kafka-consumer])]}}}}
  (:require [cheshire.core :as json]
            [cognitect.transit :as transit]
            [config.env :as cfg]
            [mount.core :as mount]
            [msgpack.core :as msg]
            [taoensso.nippy :as nippy]
            [timbre.appenders.bunyan :as log])
  (:import (java.io ByteArrayInputStream)
           (java.net InetAddress)
           (java.time Duration)
           (java.time Instant)
           (org.apache.kafka.clients.consumer ConsumerRecord KafkaConsumer
                                              OffsetAndMetadata)
           (org.apache.kafka.common TopicPartition)
           (org.apache.kafka.common.serialization ByteArrayDeserializer))
  (:gen-class))

; Default consumer configuration for development/testing
(def consumer-base-cfg
  (memoize
   (fn []
     {"bootstrap.servers" (cfg/bootstrap-servers)
      ;"client.id" (str (.getHostName (InetAddress/getLocalHost)) ":" (cfg/client-id) ":cons")
      "client.id" (cfg/client-id)
      "group.id" (cfg/group-id)
      ;"socket.keepalive.enable" "true"
      "auto.offset.reset" "earliest"
      "enable.auto.commit" (cfg/auto-commit) ; Will ALWAYS be false for create-batch-consumer
      ;"api.version.request" true
      "key.deserializer" ByteArrayDeserializer
      "value.deserializer" ByteArrayDeserializer})))

(defn consume-simple [^KafkaConsumer consumer process-record-fn]
  (let [duration (java.time.Duration/ofMillis 500)]
    (loop [records (.poll consumer duration)] ; Return every half second regardless
      (doseq [record records]
        (process-record-fn record))
      (recur (.poll consumer duration)))))

(defn lazy-msg-stream [^KafkaConsumer consumer]
  (lazy-seq
   (let [records (.poll consumer (java.time.Duration/ofMillis 1000))] ; Return once a second regardless
     (concat records (lazy-msg-stream consumer)))))

(defn update-offset-tuple
  "Based on topic, partition and offset information provided by consumer record r, update
  OffsetAndMetaData and return a [TopicPartition, OffsetAndMetadata] KV tuple for use in
  create-updated-offsets-map to build an offsets map."
  [^ConsumerRecord r]
  (log/debug "r:" r)
  [(TopicPartition. (.topic r) (.partition r)) (OffsetAndMetadata. (inc (.offset r)))])

(defn create-updated-offsets-map
  "Create an offsets map of {TopicPartition, OffsetAndMetadata} KV pairs for all
  consumer records in buffer. The map is input to .commitSync of ^KafkaConsumer
  See: https://kafka.apache.org/10/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html#commitSync(java.util.Map)"
  [buffer]
  (reduce #(conj %1 (update-offset-tuple %2)) {} buffer))

(defn process-batch [buffer process-record-fn]
  ; Naive way. Do batch SQL insertions etc. instead. map returns lazy seq. Use
  ; doall to force consumption.
  (doall (map process-record-fn buffer)))

; Small batch for manual testing. Production batch would be 100|1000 etc.
; Consumer needs to be configured for a manual commit.
(defn consume-batch
  "Consume batches of min-batch-size or what's available within a
  second. consumer-mc needs to be configured for manual commits."
  [^KafkaConsumer consumer-mc process-record-fn min-batch-size]
  (loop [lc (lazy-msg-stream consumer-mc)]
    (let [buffer (take min-batch-size lc)]
      ;(log/debug "About to process buffer: " buffer)
      (process-batch buffer process-record-fn)
      ; Ensure consumer does not auto commit. "enable.auto.commit" "false"
      (.commitSync consumer-mc (create-updated-offsets-map buffer))
      (recur (drop min-batch-size lc)))))

(defn get-kafka-artifacts
  "Return a map with key value pairs of kafka artifacts extracted from the ConsumerRecord"
  [^ConsumerRecord record]
  {:kafka-offset (.offset record)
   :kafka-partition (.partition record)
   :kafka-ts (Instant/ofEpochMilli (.timestamp record))
   :deserialize-ts (Instant/now)})

; Consumer message hydration convenience functions. All return
; an enriched map with the Kafka hydration time added to the record.
(defn hydrate-string-record [^ConsumerRecord record]
  (let [s (String. (.value record) java.nio.charset.StandardCharsets/UTF_8)   ; Convert ByteArray to a String.
        m (merge (assoc {} :value s) (get-kafka-artifacts record))]
    (log/debug "Hydrated string record:" m)
    m))

(defn hydrate-json-record [^ConsumerRecord record]
  (let [value (String. (.value record) java.nio.charset.StandardCharsets/UTF_8)   ; Convert ByteArray to a String.
        m (merge (assoc (json/parse-string value true) :orig-json value)
                 (get-kafka-artifacts record))]
    (log/debug "Hydrated json record:" m)
    m))

(defn hydrate-msgpack-record [^ConsumerRecord record]
  (let [m (merge (-> record (.value) msg/unpack)
                 (get-kafka-artifacts record))]
    (log/debug "Hydrated msgpack record:" m)
    m))

(defn hydrate-nippy-record [^ConsumerRecord record]
  (let [m (merge (-> record (.value) nippy/thaw)
                 (get-kafka-artifacts record))]
    (log/debug "Hydrated nippy record:" m)
    m))

(defn hydrate-transit-record [transit-type ^ConsumerRecord record]
  (let [in (ByteArrayInputStream. (.value record))
        reader (transit/reader in transit-type)
        m (merge (transit/read reader)
                 (get-kafka-artifacts record))]
    (log/debug "Hydrated transit-msgpack record:" m)
    m))

(defmulti
  create-record-handler
  (fn [_msg-handler msg-type & _options] msg-type))

(defmethod create-record-handler :nippy
  [msg-handler _msg-type & _options]
  (let [client-id ((consumer-base-cfg) "client.id")
        cons-group-id ((consumer-base-cfg) "group.id")]
    (fn [^ConsumerRecord record]
      (let [m (hydrate-nippy-record record)]
        (msg-handler m client-id cons-group-id)))))

(defmethod create-record-handler :msgpack
  [msg-handler _msg-type & _options]
  (let [client-id ((consumer-base-cfg) "client.id")
        cons-group-id ((consumer-base-cfg) "group.id")]
    (fn [^ConsumerRecord record]
      (let [m (hydrate-msgpack-record record)]
        (msg-handler m client-id cons-group-id)))))

(defmethod create-record-handler :transit
  [msg-handler _msg-type & {:keys [transit-type]}]
  (let [client-id ((consumer-base-cfg) "client.id")
        cons-group-id ((consumer-base-cfg) "group.id")]
    (fn [^ConsumerRecord record]
      (let [m (hydrate-transit-record
               transit-type record)]
        (msg-handler m client-id cons-group-id)))))

(defmethod create-record-handler :json
  [msg-handler _msg-type & _options]
  (let [client-id ((consumer-base-cfg) "client.id")
        cons-group-id ((consumer-base-cfg) "group.id")]
    (fn [^ConsumerRecord record]
      (let [m (hydrate-json-record record)]
        (msg-handler m client-id cons-group-id)))))

(defn create-batch-consumer
  [& [options-map]]
  (let [kafka-cons-cfg (merge (consumer-base-cfg) {"enable.auto.commit" false}
                              options-map)]
    (log/info "Consumer client config: " kafka-cons-cfg)
    (KafkaConsumer. kafka-cons-cfg)))

(defn log-consumer-state
  [^KafkaConsumer consumer]
  (let [topic-partitions (.assignment consumer)
        subscription-set (.subscription consumer)
        ; Note that it isn't possible to mix manual partition assignment (i.e. using assign) with
        ; dynamic partition assignment through topic subscription (i.e. using subscribe).
        ; See: https://kafka.apache.org/10/javadoc/index.html?org/apache/kafka/clients/consumer/KafkaConsumer.html
        topic-names (if (pos? (count subscription-set))
                      subscription-set
                      (for [^TopicPartition a-topic topic-partitions]
                        (.topic a-topic)))]

    (log/info (str "Subscription set: " subscription-set))
    (log/info (reduce #(str %1 ": " %2) "Assignments" topic-partitions))
    (log/info (reduce #(str %1 ": " %2) "Partitions by topic"
                      (for [topic-name topic-names]
                        (reduce #(str %1 ": " %2) (str "Partitions for " topic-name)
                                (.partitionsFor consumer topic-name)))))
    #_(log/info (str "Metrics: " (.metrics consumer)))))

(defn reset-consumer-topic
  "Reset a TopicPartition with topic-name and partition-number for consumer to offset or 0"
  [^KafkaConsumer consumer topic-name partition-number & [offset]]
  (log/debug "Partition" partition-number "'s next offset before reset is"
             (.position consumer (TopicPartition. topic-name partition-number)))
  (.seek consumer (TopicPartition. topic-name partition-number) (or offset 0))
  (log/debug "Partition" partition-number "'s next offset after reset is"
             (.position consumer (TopicPartition. topic-name partition-number))))

(defn assign-consumer-topic
  "Assign a TopicPartition with topic-name, partition-number and offset to consumer"
  [^KafkaConsumer consumer topic-name topic-partition offset]
  (.assign consumer [(TopicPartition. topic-name topic-partition)])
  (reset-consumer-topic consumer topic-name topic-partition offset))

(defn subscribe-consumer-topic
  "Subscribe consumer to topic-name"
  [^KafkaConsumer consumer topic-name]
  (.subscribe consumer [topic-name]))

(mount/defstate kafka-consumer
  :start (when (cfg/run-dev-mode)
           (log/debug "RUN_DEV_MODE =>" (cfg/run-dev-mode))
           (log/debug "sink-db =>" (cfg/db-url))
           (log/debug "Consumer base configuration =>" (consumer-base-cfg))))

(defn consume-messages
  "Consume messages with a KafkaConsumer consumer based on parameters passed."
  ([^KafkaConsumer consumer msg-handler msg-type & {:keys [batch-size transit-type]
                                                    :or {batch-size 1}}]
   (let [process-record (create-record-handler msg-handler msg-type
                                               (if-not (nil? transit-type) transit-type))]
     (log-consumer-state consumer)
     (consume-batch consumer process-record batch-size)))

  ([^KafkaConsumer consumer batch-size xfn]
   (log-consumer-state consumer)
   (consume-batch consumer xfn batch-size)))

(comment
  (require '(clojure [pprint :as cp]) ; cp/pprint
           '(puget [printer :as pp])) ; pp/cprint

  (def consumer (create-batch-consumer))
  (consume-messages consumer (str (cfg/topic) "-test-nippy") 'log/info :nippy :batch-size 3)
  (consume-messages consumer (str (cfg/topic) "-test-msgpack") 'log/info :msgpack)
  (consume-messages consumer (str (cfg/topic) "-test-transit-json") 'log/info
                    :transit :batch-size 5 :transit-type :json)
  (consume-messages consumer (str (cfg/topic) "-test-transit-json") 'log/info
                    :transit :transit-type :json)
  (consume-messages consumer (str (cfg/topic) "-test-transit-jv") 'log/info
                    :transit :transit-type :json-verbose)
  (consume-messages consumer (str (cfg/topic) "-test-transit-msgpack") 'log/info
                    :transit :transit-type :msgpack)

  (def neppy-consumer (create-batch-consumer "group.id" "neppy-group-id"))
  (consume-messages neppy-consumer (str (cfg/topic) "-test-json") 'log/info :json))
