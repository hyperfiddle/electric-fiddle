(ns xtdb-demo.xtdb-contrib
  (:require [missionary.core :as m]
            [xtdb.api :as xt]))

(defn latest-db>
  "return flow of latest XTDB tx, but only works for XTDB in-process mode. see
  https://clojurians.slack.com/archives/CG3AM2F7V/p1677432108277939?thread_ts=1677430221.688989&cid=CG3AM2F7V"
  [!xtdb]
  (->> (m/observe (fn [!]
                    (let [listener (xt/listen !xtdb {::xt/event-type ::xt/indexed-tx :with-tx-ops? true} !)]
                      #(.close listener))))
    (m/reductions {} (xt/latest-completed-tx !xtdb)) ; initial value is the latest known tx, possibly nil
    (m/relieve {})
    (m/latest (fn [{:keys [:xtdb.api/tx-time] :as ?tx}]
                (if tx-time (xt/db !xtdb {::xt/tx-time tx-time})
                    (xt/db !xtdb))))))
