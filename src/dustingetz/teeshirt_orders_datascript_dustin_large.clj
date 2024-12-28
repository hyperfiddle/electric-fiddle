(ns dustingetz.teeshirt-orders-datascript-dustin-large
  (:require [dustingetz.teeshirt-orders-datascript-dustin :refer
             [schema genders shirt-sizes fixtures-genders fixtures-shirt-sizes]]
            [datascript.core :as d]))

(defn rand-str [len] (apply str (take len (repeatedly #(char (+ (rand 26) 65))))))

(defn gen-orders [db N]
  (for [i (range N)]
    (let [gender (rand-nth (genders db ""))]
      {:db/id (+ 10000 i)
       :order/email (rand-str 10)
       :order/gender gender
       :order/shirt-size (rand-nth (shirt-sizes db gender ""))})))

(def conn)

(let [conn (delay
             (let [conn (d/create-conn schema)]
               @(d/transact conn fixtures-genders)
               @(d/transact conn fixtures-shirt-sizes)
               @(d/transact conn (gen-orders @conn 1000))
               (alter-var-root #'conn (constantly conn))
               conn))]
  (defn ensure-db! []
    @conn))

(comment (ensure-db!))