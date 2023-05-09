(ns app.dashboard
  (:require contrib.str
    #?(:clj [app.hunt-orders :as orders])
    [hyperfiddle.electric :as e]
    [hyperfiddle.electric-dom2 :as dom]
    [hyperfiddle.electric-ui4 :as ui]))


;(def recent-orders (atom {}))
;(def hunt-purchases (atom []))
;(def days-back (atom 30))

#?(:clj (defonce !x (atom true))) ; server state
(e/def x (e/server (e/watch !x))) ; reactive signal derived from atom

;#?(:clj (orders/get-recent-orders! recent-orders @days-back))
;#?(:clj (orders/get-hunt-purchases (vals @recent-orders)))

(e/defn gator-orders []
  (e/client
    (dom/div
      (dom/text "number type here is: "
        (case x
          true (e/client (pr-str (type 1))) ; javascript number type
          false (e/server (orders/java-fn))))) ; java number type

    (dom/div
      (dom/text "current site: "
        (if x
          "ClojureScript (client)"
          "Clojure (server)")))

    (ui/button (e/fn []
                 (e/server (swap! !x not)))
      (dom/text "toggle client/server")))

  #_(e/server
      (e/client
        (dom/link (dom/props {:rel :stylesheet :href "/dashboard.css"}))
        (dom/h1 (dom/text "GBO Dashboard"))
        (dom/h2 (dom/text "Hunt Orders")))))