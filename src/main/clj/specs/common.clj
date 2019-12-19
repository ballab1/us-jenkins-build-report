(ns specs.common
  "Specs common to all modules"
  (:require [cemerick.url :as url]
            [clojure.edn :as edn]
            [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as sgen]
            [clojure.test.check.generators :as cgen])
  (:import (com.fasterxml.uuid EthernetAddress Generators))
  (:gen-class))

(s/def ::int-string
  (s/with-gen
    (s/and
     string?
     #(re-matches #"^[-]*[0-9]*$" %))
    (fn [] (sgen/fmap str cgen/small-integer))))

(s/def ::+int-string
  (s/with-gen
    (s/and
     string?
     #(re-matches #"^[-]*[0-9]*$" %))
    (fn [] (sgen/fmap str (sgen/fmap inc cgen/nat)))))

(s/def ::epoch-time (s/or :int (s/and int? pos?) :+int-string ::+int-string))
(s/def ::json-int (s/or :int int? :int-string ::int-string))

(s/def ::uuid-string
  (s/with-gen
    (s/and
     string?
     #(not (clojure.string/blank? %))
     #(uuid? (edn/read-string (str "#uuid \"" % "\""))))
    (fn [] (sgen/fmap str cgen/uuid))))

(def UUIDv1 (Generators/timeBasedGenerator (EthernetAddress/fromInterface)))

(s/def ::uuid-v1-string
  (s/with-gen
    (s/and
     string?
     #(not (clojure.string/blank? %))
     #(uuid? (edn/read-string (str "#uuid \"" % "\""))))
    (fn [] (sgen/fmap (fn [_] (str (.generate UUIDv1))) cgen/small-integer))))

(s/def ::instant-string
  (s/and
   string?
   #(not (clojure.string/blank? %))
   #(inst? (edn/read-string (str "#inst \"" % "\"")))))

(def gen-utc-instant-str
  (sgen/fmap #(-> (java.time.Instant/ofEpochMilli %)
                  (java.time.OffsetDateTime/ofInstant java.time.ZoneOffset/UTC)
                  (str))
             cgen/large-integer))

(s/def ::utc-instant-string
  (s/with-gen
    ::instant-string
    (constantly gen-utc-instant-str)))

;https://gfredericks.com/speaking/2017-10-12-generators.pdf
(def gen-datetime-components
  (sgen/hash-map
   :year   (sgen/fmap #(+ % 2019) cgen/small-integer)
   :month  (sgen/large-integer* {:min 1, :max 12})
   :day    (sgen/large-integer* {:min 1, :max 31})
   :hour   (sgen/large-integer* {:min 0, :max 23})
   :minute (sgen/large-integer* {:min 0, :max 59})
   :second (sgen/large-integer* {:min 0, :max 59})
   :millis (sgen/large-integer* {:min 0, :max 1000})))

(defn construct-datetime
  [{:keys [year month day
           hour minute second millis]}]
  (try
    (java.time.Instant/parse
     (format "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ"
             year month day
             hour minute second millis))
    (catch Exception _e
      ;; kind of dumb, but it works and it's easy
      (java.time.Instant/parse
       (format "%04d-%02d-%02dT%02d:%02d:%02d.%03dZ"
               year month 28
               hour minute second millis)))))

(def gen-utc-near-datetime
  (sgen/fmap construct-datetime
             gen-datetime-components))

(s/def ::utc-near-instant-string
  (s/with-gen
    ::instant-string
    (fn [] (sgen/fmap str gen-utc-near-datetime))))

(defn non-empty-string-alphanumeric
  []
  (sgen/such-that #(not= "" %)
                  (sgen/string-alphanumeric)))

(def gen-fq-file
  "Generator for fully qualified path names."
  (sgen/fmap #(->> %
                   (interleave (repeat "/"))
                   (apply str))
             (sgen/not-empty
              (sgen/vector
               (non-empty-string-alphanumeric)))))

(s/def ::fq-filename-string
  (s/with-gen
    (s/and
     string?
     #(re-matches #"^(.*/)?(?:$|(.+?)(?:(\.[^.]*$)|$))" %))
    (fn [] (sgen/fmap str gen-fq-file))))

(defn gen-url
  "Generator for generating URLs; note that it may generate
  http URLs on port 443 and https URLs on port 80, and only
  uses alphanumerics"
  []
  (sgen/fmap
   (partial apply (comp str url/->URL))
   (sgen/tuple
    ;; protocol
    (sgen/elements #{"http" "https"})
    ;; username
    (sgen/string-alphanumeric)
    ;; password
    (sgen/string-alphanumeric)
    ;; host
    (sgen/string-alphanumeric)
    ;; port
    (sgen/choose 1 65535)
    ;; path
    (sgen/fmap #(->> %
                     (interleave (repeat "/"))
                     (apply str))
               (sgen/not-empty
                (sgen/vector
                 (non-empty-string-alphanumeric))))
    ;; query
    (sgen/map
     (non-empty-string-alphanumeric)
     (non-empty-string-alphanumeric)
     {:max-elements 2})
    ;; anchor
    (sgen/string-alphanumeric))))

(s/def ::url-string (s/with-gen
                      (s/and string?
                             #(try
                                (url/url %)
                                (catch Throwable _t false)))
                      gen-url))
