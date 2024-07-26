(ns agents.ports
  (:refer-clojure :exclude [apply])
  (:require
   [hyperfiddle.electric :as e]
   [missionary.core :as m]
   [hyperfiddle.electric-local-def :as local])
  #?(:cljs (:require-macros agents.ports)))

;;; Ports
(defonce !ports (atom {}))

(comment
  @!ports
  )

(e/def ports (e/watch !ports))

(defn add-port [ports fsym f] (assoc ports fsym {::F f}))
(defn remove-port [ports fsym] (dissoc ports fsym))

(defn run-port [ports fsym call-id]
  (let [!result (atom nil)]
    (update ports fsym assoc-in [::calls call-id]
      {::!args   (atom [])
       ::!result !result,
       ::signal  (->> (m/watch !result)
                   (m/eduction (remove #{::init}))
                   (m/reductions {} nil)
                   (m/stream))})))

(defn stop-port [ports fsym args] (update ports fsym update ::calls dissoc args))

(e/defn Register [fsym F]
  (swap! !ports add-port fsym F)
  (e/on-unmount #(swap! !ports remove-port fsym))
  (e/for-by key [[_instance-id {::keys [!result !args]}] (get-in ports [fsym ::calls])]
    (try (reset! !result (new F (e/watch !args)))
         (catch hyperfiddle.electric.Pending _))))

(local/def local-ports (e/watch !ports))

(local/def args)

(local/defn RegisterLocal [fsym f]
  (swap! !ports add-port fsym f)
  (e/on-unmount #(swap! !ports remove-port fsym))
  (e/for-by key [[_instance-id {::keys [!result !args]}] (get-in local-ports [fsym ::calls])]
    (binding [args (e/watch !args)]
      (try (let [result (new (clojure.core/apply f args))]
             ;; (when-not (= ::init result))
             (reset! !result result))
           (catch hyperfiddle.electric.Pending _)))))

(def next-call-id (partial swap! (atom 0) inc))

(e/defn Call [fsym args]
  (let [call-id (next-call-id)]
    (swap! !ports run-port fsym call-id)
    (e/on-unmount #(swap! !ports stop-port fsym call-id))
    (when-let [{::keys [!args signal]} (get-in ports [fsym ::calls call-id])]
      (prn "Call " fsym " with " args)
      (reset! !args args)
      (new signal))))

;; Temporary workarounds until we get a better single-peer entrypoint
;; Current local/run entrypoint cannot resolve e/defn, only local/defn

(defn ^:no-doc -splicev [args] (if (empty? args) args (into [] cat [(pop args) (peek args)])))

(local/defn Apply* [F args] ; we use `defn*` instead of e/def e/fn* for better stacktraces
  (let [spliced (-splicev args)]
    (case (count spliced)
      0 (new F)
      1 (new F (nth spliced 0))
      2 (new F (nth spliced 0) (nth spliced 1))
      3 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2))
      4 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3))
      5 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4))
      6 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5))
      7 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6))
      8 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7))
      9 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8))
      10 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9))
      11 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10))
      12 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11))
      13 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12))
      14 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12) (nth spliced 13))
      15 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12) (nth spliced 13) (nth spliced 14))
      16 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12) (nth spliced 13) (nth spliced 14) (nth spliced 15))
      17 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12) (nth spliced 13) (nth spliced 14) (nth spliced 15) (nth spliced 16))
      18 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12) (nth spliced 13) (nth spliced 14) (nth spliced 15) (nth spliced 16) (nth spliced 17))
      19 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12) (nth spliced 13) (nth spliced 14) (nth spliced 15) (nth spliced 16) (nth spliced 17) (nth spliced 18))
      20 (new F (nth spliced 0) (nth spliced 1) (nth spliced 2) (nth spliced 3) (nth spliced 4) (nth spliced 5) (nth spliced 6) (nth spliced 7) (nth spliced 8) (nth spliced 9) (nth spliced 10) (nth spliced 11) (nth spliced 12) (nth spliced 13) (nth spliced 14) (nth spliced 15) (nth spliced 16) (nth spliced 17) (nth spliced 18) (nth spliced 19)))))

(defmacro apply [F & args]
  (assert (not (empty? args)) (str `apply " takes and Electric function and at least one argument. Given 0.")) ; matches clojure behavior
  `(new Apply* ~F [~@args]))

(local/defn InjectArgs [F]
  (e/fn [] (apply F args)))
