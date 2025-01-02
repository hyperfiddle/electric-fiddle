(ns dustingetz.trivial-datascript-form
  (:require [datascript.core :as d]
            [hyperfiddle.electric3 :as e]))

(def fixtures
  [{:db/id 42 :user/str1 "one" :user/num1 1 :user/bool1 true}])

(def test-conn (delay (let [conn (d/create-conn {})]
                        @(d/transact conn fixtures)
                        conn)))

(def test-db (delay @@test-conn))

; works on client or server in principle
(defn ensure-conn! [] @test-conn)
(defn ensure-db! [] @(ensure-conn!))

#?(:clj (defn transact-unreliable [!conn tx
                                   & {:keys [slow fail]
                                      :or {slow false fail false}}]
          (when (true? slow) (Thread/sleep 1000)) ; java only
          (when (true? fail) (throw (ex-info "artificial failure" {})))
          (d/transact! !conn tx)))

(e/defn Query-record [db id forms]
  (e/server (e/Offload #(d/pull db [:user/str1 :user/num1 :user/bool1] id))))
