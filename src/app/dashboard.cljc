(ns app.dashboard
  (:require contrib.str
            #?(:clj [app.hunt-orders :as orders])
            [hyperfiddle.electric :as e]
            [hyperfiddle.electric-dom2 :as dom]
            [hyperfiddle.electric-ui4 :as ui]
            [clojure.string :as string]))


; server state
#?(:clj (defonce !days-back (atom 20)))
#?(:clj (defonce !recent-orders (atom {})))
#?(:clj (defonce !hunt-purchases (atom [])))

; reactive signal derived from atom
;(e/def recent-orders (e/server (e/watch !recent-orders)))
(e/def hunt-purchases (e/server (e/watch !hunt-purchases)))
(e/def query-status (e/server (e/watch orders/!query-status)))
(e/def days-back (e/server (e/watch !days-back)))

(def header-keys
  [#_:base-total #_:email #_:phone :order-id #_:refunded-amount :date-created #_:product-id :name :quantity #_:first-name :preferred-date #_:country-iso-2 :last-name :payment-method :city :state :price-inc-tax :total-inc-tax #_:payment-provider-id :payment-status #_:staff-notes :geoip-country :country #_:refund-amount #_:company])

(e/defn gator-orders []
  (e/client
    (dom/h1
      (dom/text "Recent Orders: "))
        ;(when recent-orders
        ;  (pr-str recent-orders))))

    (dom/div
      (dom/text "Status: " query-status " ")
      (ui/button
        (e/fn []
          (e/server
            (reset! orders/!query-status "Reset Status")))
        (dom/text "Reset Status")))

    (dom/div

      (ui/button
        (e/fn []
          (e/server
            (orders/get-recent-orders! !recent-orders @!days-back)))
        (dom/style {:display "inline-block"
                    :margin-right "10px"})
        (dom/text "Get Orders"))

      (ui/button
        (e/fn []
          (e/server
            (reset! !recent-orders {})))
        (dom/style {:display "inline-block"
                    :margin-right "10px"})
        (dom/text "Clear Orders"))

      (dom/text "Orders Counted: "
        (e/server (count @!recent-orders))))

    (dom/div
      (dom/text "How many days to go back: " days-back " ")
      (ui/long
        (clojure.math/round days-back)
        (e/fn [v]
          (e/server
            (reset! !days-back v)))))

    (dom/div
      (ui/button
        (e/fn []
          (e/server
            (e/offload
              #(orders/get-hunt-purchases
                 (vals @!recent-orders) !recent-orders !hunt-purchases))))
        (dom/style {:display "inline-block"
                    :margin-right "10px"})
        (dom/text "Find Hunt Orders"))
      (ui/button
        (e/fn []
          (e/server
            (reset! !hunt-purchases [])))
        (dom/text "Clear Hunt Orders")))

    (dom/div
      (dom/text "Recent Hunt Orders: ")
      (dom/table
        (dom/style {:background-color "#e5f7fd"
                    :border "1px solid black"})
        (dom/thead
          (dom/tr
            (e/for [h header-keys]
              (dom/td (dom/text (name h))))))
        (dom/tbody
            (e/for [purchase (reverse (sort-by :order-id hunt-purchases))]
              (dom/tr
                (e/for [h header-keys]
                  (let [v (purchase h)]
                    (dom/td
                      (dom/style
                        {:border "1px solid black"
                         :padding "0 5px"
                         :text-align "center"
                         :background-color "white"})
                      (case h
                        :date-created ; "Thu, 11 May 2023 20:20:42 +0000"
                        (dom/text
                          (-> v
                            (string/split #" ")
                            (->>
                              (take 4)
                              (interpose " ")
                              (apply str))))
                        :order-id
                        (dom/a
                          (dom/props
                            {:href
                             (str "https://store-7hstasnrjg.mybigcommerce.com/manage/orders/" (purchase h))})
                          (dom/text v))
                        (dom/text v))))))))))))

