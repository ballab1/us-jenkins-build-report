;; See: https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-within-NRepl
;; See: https://github.com/bhauman/lein-figwheel/wiki/Using-the-Figwheel-REPL-with-Vim
;; See: https://clojurescript.org/guides/quick-start

;; NOTE: ClojureScript still requires Clojure 1.8.

;; Provide three REPLs in one
(ns user
  (:require [clojure.tools.nrepl.server :refer (start-server stop-server)]
            [environ.core :refer [env]]
            [figwheel-sidecar.repl-api :as fr]
            [cemerick.piggieback :as piggie]
            [server-handler]
            [immutant.web :as iweb]))

;; See: https://github.com/bhauman/lein-figwheel/wiki/Configuration-Options
;; See: https://github.com/bhauman/lein-figwheel/blob/master/sidecar/src/figwheel_sidecar/schemas/config.clj
(def figwheel-config
  {:figwheel-options {:nrepl-port (if-let [port (env :nrepl-port)]  ;; <-- figwheel server config here
                                    (if (.equals "" port) 7888 (Integer. port)) 7888)
                      ; One server for ALL THREE repl interfaces: cider, refactor and piggieback
                      :nrepl-middleware ["cider.nrepl/cider-middleware"
                                         "refactor-nrepl.middleware/wrap-refactor"
                                         "cemerick.piggieback/wrap-cljs-repl"]
                      ;;NOTE: ensure "resources" is a root in classpath!
                      ;:http-server-root defaults to "public" under "resources"
                      :open-file-command "~/bin/figwheel-file-opener"
                      :server-logfile "tmp/logs/figwheel-server.log"
                      ; Provide a ring-handler to figwheel's http-kit
                      ;:ring-handler 'server-handler/dev-app ; dev
                      ;:ring-handler 'server-handler/app     ; prod
}
   :build-ids ["dev"]   ;; <-- a vector of build ids to start autobuilding
   :all-builds          ;; <-- supply your build configs here
   [{:id "dev"
     :figwheel true
     ;:figwheel {:open-urls ["http://localhost:3449/index.html"]}
     :source-paths ["src/cljs"]
     ; See hello-world example here: https://clojurescript.org/guides/quick-start
     ; Open the index.html file in the browser and watch the browser Console
     :compiler {:main "hello-world.core"
                :asset-path "js/out"
                :output-to "resources/public/js/hello-world.js"
                :output-dir "resources/public/js/out"}}]})

(defn start-fig [] (fr/start-figwheel! figwheel-config))

(defn stop-fig [] (fr/stop-figwheel!))

(defn cljs [] (fr/cljs-repl "dev"))

;; Start during initialization instead of from the nREPL. To enable for *.cljs
;; in vim-fireplace type ":Piggieback (figwheel-sidecar.repl-api/repl-env)"
(start-fig)

;; If figwheel config does not provide a ring-handler to http-kit, start
;; immutant/undertow in either development or production mode
(if-not (get-in figwheel-config [:figwheel-options :ring-handler])
  (def immutant-instance
    (if (env :run-dev-mode)
      (iweb/run-dmc server-handler/dev-app) ; dev
      (iweb/run server-handler/app))))      ; prod
