(ns msa.db.pg
  "Core PostgreSQL database interaction"
  (:require [clojure.java.jdbc :as jdbc]
            [config.env :as cfg]
            [mount.core :refer [defstate]]
            [timbre.appenders.bunyan :as log])
  (:import (com.zaxxer.hikari HikariDataSource))
  (:gen-class))

;; A generic data pool
(defn pooled-datasource []
  (let [pds (doto (HikariDataSource.)
              (.setJdbcUrl (cfg/db-url))
              (.setUsername (cfg/db-user))
              (.setPassword (cfg/db-password))
              (.setMaximumPoolSize 5)
              ; 24 hours for a uS
              (.setConnectionTimeout 86400000)
              ; 1 minute
              (.setIdleTimeout 60000))]
    (log/debug (format "jdbcUrl is %s" (cfg/db-url)))
    {:datasource pds}))

(defn ds-close
  "Close a Hikari datasource"
  [ds]
  (.close ^HikariDataSource (:datasource ds)))

; Aggregation sink DB. Target set through environment.
(defstate sink-db
  :start (pooled-datasource)
  :stop (ds-close sink-db))

(defn simple-query
  "Pass the query as a SQL statement"
  [query]
  (into [] (jdbc/query sink-db [query])))
