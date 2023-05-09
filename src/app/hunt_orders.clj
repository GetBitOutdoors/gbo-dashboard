(ns app.hunt-orders
  (:require
    [clojure.set :as set]
    [clojure.string :as string]
    [tick.core :as t]
    [clj-http.client :as http]
    [cheshire.core :as json]
    [clojure.pprint :refer [pprint]]
    [camel-snake-kebab.core :as csk]))
    ;[aero.core :refer [read-config]])

;(read-config "config.edn")

(def store-hash (System/getenv "STORE_HASH"))
(def order-read-only-token (System/getenv "ORDERS_API_TOKEN"))
(def api-base-url
  (str "https://api.bigcommerce.com/stores/" store-hash "/"))

(def default-http-headers
  {:accept "application/json"
   :content-type "application/json"
   :x-auth-token order-read-only-token})

(defn encode-json [edn]
  "nested clj data -> snake case string json"
  ;same as json/generate-string
  (json/encode edn
    {:key-fn csk/->snake_case_string}))


(defn decode-json [s]
  "json -> kebab case keyword data"
  ;same as json/parse-string
  (json/decode s csk/->kebab-case-keyword))

(defn map-vals [f m] (reduce-kv (fn [m k v] (assoc m k (f v))) {} m))

(defn map-keys [f m] (reduce-kv (fn [m k v] (assoc m (f k) v)) {} m))


(defn catch-exception [f params]
  (try (apply f params)
    (catch Exception e e)))

(defn response
  [{:keys [method api endpoint params headers body debug? token]
    :or {api "v3/catalog/"
         body nil
         headers {}
         debug? false}
    :as req}]
  (let
    [http-method-fns
     {:get http/get
      :put http/put
      :post http/post
      :delete http/delete}
     endpoint
     (if (vector? endpoint)
       (->> endpoint
         (map #(if (keyword? %) (name %) %))
         (interpose "/")
         (apply str))
       endpoint)
     api-url (str api-base-url api endpoint)
     headers
     (merge default-http-headers headers)
     params
     (cond-> {:headers headers}
       token
       (assoc-in [:headers :x-auth-token] token)
       body
       (assoc :body (encode-json body))
       params
       (assoc :query-params
         ;todo: fix, does clj-http do this already?
         ;(nested-kebab-keys->snake-keyword-strings params)
         (map-keys csk/->snake_case_string params))
       debug?
       (assoc
         :debug true
         :debug-body true))]
    (catch-exception
      (http-method-fns method) [api-url params])))


(defn decode-body [k response]
  (-> response :body decode-json k))

(defn response-data [req]
  (->> (response req)
    (decode-body :data)))

(defn response-meta [req]
  (->> (response req)
    (decode-body :meta)))

(defn response-body [req]
  (->> (response req)
    :body
    decode-json))
;----------------------

(def gator-hunt-product-ids
  #{6433 6435 6436 6437 6438 8253 8256})
(def hog-hunt-product-ids
  #{6474 6475})
(def hunt-product-ids
  (set/union gator-hunt-product-ids hog-hunt-product-ids))

(defn inst->bc-timestamp [inst]
  (-> inst
    (t/truncate :seconds)
    (t/offset-by -5)
    str
    (string/replace ":00-" ":00:00-")))

(defn cut-off-date-time [days-back]
  (inst->bc-timestamp
    (t/<< (t/now)
      (t/new-duration days-back :days))))

(defn get-page-of-orders [page-number days-back]
  (response-body
    {:method :get
     :params
     {:page page-number
      :min-date-created (cut-off-date-time days-back)
      :limit 50}
     :token order-read-only-token
     :api "v2/orders/"}))

(comment
  (response
    {:method :get
     :params
     {:page 2
      :min-date-created (cut-off-date-time 5)
      :limit 50}
     :token order-read-only-token
     :api "v2/orders/"}))


(defn get-recent-orders! [orders-atom days-back]
  (loop [page-number 1]
    (let
      [page-of-orders (get-page-of-orders page-number days-back)]
      (if
        (some? page-of-orders)
        (do
          (pprint (str "page " page-number " of orders..."))
          (swap! orders-atom
            #(merge %
               (->> page-of-orders
                 (mapv
                   (fn [{order-id :id :as order}]
                     [order-id order]))
                 (into {}))))
          (recur (inc page-number)))
        (println "get recent orders ended")))))


(defn get-order-products [{order-id :id :as order}]
  (response-body
    {:method :get
     :token order-read-only-token
     :endpoint [order-id :products]
     :api "v2/orders/"}))


(defn hunt-option-data [{options :product-options :as product}]
  "returns map"
  (if options
    (first
      (map
        (fn [{:keys [display-name display-value] :as option}]
          (cond-> {}
            (string/includes? display-name "Hunt Date")
            (assoc  :preferred-date display-value)
            (string/includes? display-name "Hog")
            (assoc  :hog-combo display-value)))
        options))
    {}))


;todo: get customer info and shipping address
;use ip address to attain location?
;make billing address values explicit (w ns keys?)
;separate product refunded value from order refunded value
(defn hunt-purchase [{:keys [order-id] :as hunt-product} orders-atom]
  (let [order (@orders-atom order-id)]
    (merge
      (-> order :billing-address
        (dissoc :form-fields :street-2 :street-1 :zip))
      (-> order
        (select-keys [:date-created :refunded-amount :payment-method :total-inc-tax :payment-provider-id :payment-status :geoip-country :staff-notes]))
      (-> hunt-product
        (select-keys [:base-total :quantity  :name  :total-inc-tax #_:base-price :price-inc-tax :order-id :product-id :refund-amount #_:variant-id]))
      (hunt-option-data hunt-product))))


(defn hunt-product? [{:keys [product-id]}]
  (some? (hunt-product-ids product-id)))


(defn get-hunt-purchases [orders orders-atom hunt-purchases-atom]
  (doseq [order orders]
    (pprint "getting products...")
    (let
      [order-products (get-order-products order)
       hunt-products-in-order
       (->> order-products
         (filter hunt-product?)
         (mapv
           (fn [hunt-product]
             (hunt-purchase hunt-product orders-atom))))]
      (if (seq hunt-products-in-order)
        (do
          (println "found hunt order...")
          (println hunt-products-in-order)
          (swap! hunt-purchases-atom
            #(vec (concat % hunt-products-in-order))))
        (println "no hunt products in this order...")))))

(comment
  (def recent-orders (atom {}))
  (deref recent-orders)
  (def hunt-purchases (atom []))
  (def days-back (atom 5))
  (get-recent-orders! recent-orders @days-back))