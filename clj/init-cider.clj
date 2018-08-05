(ns user
  (:require [clojure.tools.nrepl.server :refer (start-server stop-server)]
            [cider.nrepl :refer (cider-nrepl-handler)]))

; Start a cider nrepl for vim-fireplace. Requires java 1.8 for 'gf' to work in vim-fireplace.
(defonce server (start-server :port (Integer. (or (System/getenv "NREPL_PORT") 7888))
                              :bind "localhost"                  ; avoid ipv4 vs ipv6 issues
                              :handler cider-nrepl-handler))
