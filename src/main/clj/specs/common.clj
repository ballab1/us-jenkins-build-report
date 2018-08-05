(ns specs.common
  "Specs common to all modules"
  (:require [clojure.edn :as edn]
            [clojure.spec.alpha :as s])
  (:gen-class))

(s/def ::int-string
  (s/and
   string?
   #(re-matches #"^[0-9]*$" %)))

(s/def ::epoch-time (s/or :int int? :int-string ::int-string))
(s/def ::json-int (s/or :int int? :int-string ::int-string))

(s/def ::uuid-string
  (s/and
   string?
   #(uuid? (edn/read-string (str "#uuid \"" % "\"")))))

(s/def ::date-string
  (s/and
   string?
   #(inst? (edn/read-string (str "#inst \"" % "\"")))))
