(ns config.env
  (:require [aero.core :as aero]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.java.shell :as shell]
            [manifest.core :as manifest])
  (:gen-class))

(defn read-config
  "Read config.edn from the classpath creating a configuration based on profile"
  [profile]
  ; If a java string is passed, make it a keyword, keywords stay keywords.
  (aero/read-config (clojure.java.io/resource "config.edn") {:profile (keyword profile)}))

(def config (read-config (or (when-let [cfg_prof (System/getenv "CONFIG_PROFILE")]
                              ; env vars are simple strings. Make it an actual
                              ; clojure type
                              (edn/read-string cfg_prof))
                             :dev)))

(defn webserver-port []
  (get-in config [:webserver :port]))

(defn client-id []
  (config :client-id))

(defn group-id []
  (config :group-id))

(defn db-url []
  (get-in config [:db :url]))

(defn db-user []
  (get-in config [:db :user]))

(defn db-password []
  (get-in config [:db :password]))

(defn bootstrap-servers []
  (get-in config [:kafka :bootstrap-servers]))

(defn topic []
  (config :topic))

(defn dlq-topic []
  (str (config :topic) "-dlq"))

(defn auto-commit []
  (config :auto-commit))

(defn hb-topic []
  (config :hb-topic))

(defn run-dev-mode []
  (or (System/getenv "RUN_DEV_MODE") false)
  #_(config :run-dev-mode))

(defn max-log-size-bytes []
  (or (config :max-log-size-bytes) (* 1024 1024 10)))  ; Make default 10 Megs

(defn max-log-files []
  (or (config :max-log-files) 10))  ; Make default 10 log files

(defn timbre-log-level []
  ; handle env var string to keyword conversion automatically.
  (or (if-let [level (get-in config [:timbre :level])]
        (if (string? level)
          (edn/read-string level)
          level))
      :debug))

(defn timbre-ns-whitelist []
  (get-in config [:timbre :ns-whitelist] []))

(defn timbre-ns-blacklist []
  (get-in config [:timbre :ns-blacklist] []))

(def get-manifest
  "Keywordizes the manifest, assumed to be on the classpath"
  (memoize
   (fn []
      ;; Provide main Java class name, so use _ for -.
     (manifest/manifest "msa.core"))))

(def get-jre
  (memoize
   (fn []
     (let [jv (System/getProperty "java.version")
           vendor (System/getProperty "java.vendor")
           vm-version (System/getProperty "java.vm.version")
           jre (format "%s (%s %s)" jv vendor vm-version)]
       jre))))

(def revision
  (memoize
   (fn []
     (if-let [revision (:SCM-Revision (get-manifest))]
       revision
       (if (.exists (io/file ".revision"))
         (if-let [revision (clojure.string/trim-newline (slurp ".revision"))]
           (if (clojure.string/blank? revision)
             "No-SCM"
             revision))
         (let [result (shell/sh "git" "describe" "--tag" "--dirty")]
           (if (= (:err result) 0)
             (clojure.string/trim-newline (:out result))
             "No-SCM")))))))
