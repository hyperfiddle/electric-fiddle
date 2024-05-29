(ns hello-fiddle.events
  (:require
   [contrib.debug]
   [contrib.str]
   [hello-fiddle.stage :as stage]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [missionary.core :as m]))

#?(:cljs
   (defn listen "Takes the same arguments as `addEventListener` and returns an uninitialized
  missionary flow that handles the listener's lifecycle producing `(f e)`.
  Relieves backpressure. `opts` can be a clojure map."
     ([node event-type] (listen node event-type identity))
     ([node event-type f] (listen node event-type f {}))
     ([node event-type f opts]
      (->> (m/observe (fn [!]
                        (let [! #(! (f %)), opts (clj->js opts)]
                          (.addEventListener node event-type ! opts)
                          #(.removeEventListener node event-type ! opts))))
        (m/relieve {})))))

;; FIXME stabilize `f` to prevent DOM eventListener instance trashing
(e/defn EventListener "Takes the same arguments as `addEventListener`. Returns
the result of `(f event)`.

```clj
(dom/input
  (when-some [v ($ EventListener \"input\" #(-> % .-target .-value))]
    (prn v)))
```"
  ([event-type] (new EventListener event-type identity))
  ([event-type f] (new EventListener dom/node event-type f))
  ([node event-type f] (new EventListener node event-type f {}))
  ([node event-type f opts] (new EventListener node event-type f opts nil))
  ([node event-type f opts init-v]
   (e/client (new (m/reductions {} init-v (listen node event-type f opts))))))


(defn set-releaser! [!release! v down?]
  (when-not (down? v) (compare-and-set! !release! nil #(reset! !release! nil))))

(e/defn LatchingRelay
  ([v] (new LatchingRelay v nil?))
  ([v down?]
   (let [!release! (atom nil)]
     (set-releaser! !release! v down?)
     (e/watch !release!))))


(defn set-held! [!held v down?]
  (let [[held-v release! :as held] @!held]
    (if (or release! (down? v))
      held
      (compare-and-set! !held held [v #(swap! !held assoc 1 nil)]))))

(e/defn DLatchingRelay
  ([v] (new DLatchingRelay v nil?))
  ([v down?]
   (let [!held (atom [(e/snapshot v) nil])]
     (set-held! !held v down?)
     (e/watch !held))))

(e/defn Sampler [Body]
  (let [!sampled (atom nil)
        !samplee (atom nil)]
    (reset! !samplee (Body. (fn [& _] (reset! !sampled @!samplee) nil)))
    (e/watch !sampled)))

(e/defn Stage [Body] ; a looping variant of Sampler
  (let [!sampled (atom nil)
        !samplee (atom nil)]
    (let [samplee (e/watch !samplee)]
      (reset! !samplee (Body. samplee
                         (fn [& _] (reset! !sampled @!samplee) (reset! !samplee nil) nil) ; commit
                         (fn [& _] (reset! !samplee nil) nil)))) ; discard
    (e/watch !sampled)))

(e/defn Events []
  (e/client
    (dom/h1 (dom/text "Latches"))
    (dom/h2 (dom/text "Event Listener"))
    (let [v (dom/input
              (new EventListener "input" #(.. % -target -value)))]
      (dom/pre (dom/text v)))

    (dom/h2 (dom/text "Latching Relay"))
    (let [v        (dom/input
                     (new EventListener "input" #(.. % -target -value)))
          release! (LatchingRelay. v)]
      (dom/button (dom/text "Release!")
                  (new EventListener "click" (fn [_] (when release! (release!)))))
      (dom/pre (dom/text (contrib.str/pprint-str {:input-value v, :latch (some? release!)}))))

    (dom/h2 (dom/text "D-Latching Relay"))
    (let [v                    (dom/input (new EventListener "input" #(.. % -target -value)))
          [latched-v release!] (DLatchingRelay. v)]
      (dom/button (dom/text "Release!")
                  (new EventListener "click" (fn [_] (when release! (release!)))))
      (dom/pre (dom/text (contrib.str/pprint-str {:input-value   v
                                                  :latched-value latched-v
                                                  :latch         (some? release!)}))))

    (dom/h2 (dom/text "Stage"))
    (let [!comitted-value (atom :init)
          committed       (e/watch !comitted-value)]
      (stage/Stage. (e/fn [v] (prn "commited" v) (reset! !comitted-value v))
        (e/fn []
          (let [v (dom/input (new EventListener "input" #(.. % -target -value)))]
            (stage/stage! v)
            (dom/button (dom/text "commit")
                        (when-let [e (EventListener. "click")]
                          (let [release! (LatchingRelay. e)]
                            (when release!
                              (prn "commit!")
                              (case (stage/Commit.)
                                (release!))))))
            (dom/button (dom/text "discard")
                        (EventListener. "click" (fn [_] (prn "discard!") (stage/discard!))))
            (dom/pre (dom/text (contrib.str/pprint-str {:stage       stage/stage
                                                        :input-value v}))))))
      (contrib.debug/dbg 'committed committed)
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed}))))

    (dom/h2 (dom/text "Sampler"))
    (let [committed
          (Sampler.
            (e/fn [sample!]
              (let [v (dom/input (new EventListener "input" #(.. % -target -value)))]
                (dom/button (dom/text "sample") (EventListener. "click" sample!))
                (dom/pre (dom/text (contrib.str/pprint-str {:input-value v})))
                v)))]
      (contrib.debug/dbg 'committed committed)
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed}))))

    (dom/h2 (dom/text "Stage2"))
    (let [committed
          (Stage.
            (e/fn [stage commit! discard!]
              (let [v (dom/input
                        (set! (.-value dom/node) stage)
                        (new EventListener "input" #(.. % -target -value)))]
                (dom/button (dom/text "commit!") (EventListener. "click" commit!))
                (dom/button (dom/text "discard!") (EventListener. "click" discard!))
                (dom/pre (dom/text (contrib.str/pprint-str {:stage stage})))
                v)))]
      (contrib.debug/dbg 'committed committed)
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed}))))

    (dom/h2 (dom/text "Rollback"))
    (let [!committed (atom "authoritative")
          committed  (e/watch !committed)]
      (def c !committed)
      (when-let [committed (Stage.
                             (e/fn [stage commit! discard!]
                               (let [value (or stage committed)
                                     value     (dom/input
                                                 (set! (.-value dom/node) value)
                                                 (or (new EventListener "input" #(.. % -target -value))
                                                   value))]
                                 (dom/button (dom/text "commit!") (EventListener. "click" commit!))
                                 (dom/button (dom/text "discard!") (EventListener. "click" discard!))
                                 (dom/pre (dom/text (contrib.str/pprint-str {:stage stage})))
                                 value)))]
        (reset! !committed committed))
      (contrib.debug/dbg 'committed committed)
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed}))))

    (dom/h2 (dom/text "Stage3"))
    (let [!committed (atom "authoritative"), committed (e/watch !committed)
          in (dom/input (set! (.-value dom/node) committed) dom/node)]
      (dom/button (dom/text "commit!") (EventListener. "click" (fn [_] (reset! !committed (.-value in)))))
      (dom/button (dom/text "discard!") (EventListener. "click" (fn [_] (set! (.-value in) @!committed))))
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed}))))

    (dom/h2 (dom/text "Stage with server value"))
    (e/server
      (let [!committed (atom "authoritative"), committed (e/watch !committed)]
        (e/client
          (let [in (dom/input (set! (.-value dom/node) committed) dom/node)]
            (dom/button (dom/text "commit!")
              (let [e (EventListener. "click")
                    release! (LatchingRelay. e)]
                (when release!
                  (case (e/server (reset! !committed (e/client (.-value in)))) (release!)))))
            (dom/button (dom/text "discard!")
              (let [e (EventListener. "click")
                    release! (LatchingRelay. e)]
                (when release!
                  (case (set! (.-value in) committed) (release!)))))
            (dom/pre (dom/text (contrib.str/pprint-str {:committed committed})))))))))
