;; Goal: implement Sampler, Latch, Relay, DLatch?, LatchingRelay?, DLatchingRelay?
;; Current versions are mixing layers.

(ns hello-fiddle.electronics
  (:require
   [contrib.debug]
   [contrib.str]
   ;; [hello-fiddle.stage :as stage]
   [hyperfiddle.electric :as e]
   [hyperfiddle.electric-dom2 :as dom]
   [missionary.core :as m]))

(defn capture-fn
  "Captures variability of a function under a stable identity.
  Return a proxy to the captured function.
  Use case: prevent unmount and remount when a cc/fn argument updates due to an inner variable dependency."
  []
  (let [!state (object-array 1)
        ret (fn [& args] (apply (aget !state 0) args))]
    (fn [x]
      (aset !state 0 x)
      ret)))

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
   (e/client (new (m/reductions {} init-v (listen node event-type ((capture-fn) f) opts))))))

(e/defn Sampler [Body] ; used by Stage
  (let [!sampled (atom nil)
        !samplee (atom nil)]
    (reset! !samplee (Body. (fn ([] (reset! !sampled @!samplee) nil) ([x] (reset! !sampled x) x))))
    (e/watch !sampled)))

(defn set-releaser! [!release! v down?]
  (when-not (down? v) (compare-and-set! !release! nil #(reset! !release! nil))))

(e/defn FlipFlop ; use by Pulse
  ([control] (new FlipFlop control nil?))
  ([control down?]
   (let [!release! (atom nil)]
     (set-releaser! !release! control down?)
     (e/watch !release!))))

(e/defn Latch [Body] ; for event handlers
  (let [!latch (atom false)
        v (Body. #(reset! !latch true) #(reset! !latch false))]
    (if (e/watch !latch)
      (e/snapshot v)
      v)))

(defn set-held! [!held v down?]
  (let [[_held-v release! :as held] @!held]
    (if (or release! (down? v))
      held
      (compare-and-set! !held held [v (fn rec ([] (rec nil)) ([x] (swap! !held assoc 1 nil) x))]))))

(e/defn AutoLatch ; for event handlers
  ([v] (AutoLatch. v nil?))
  ([v down?]
   (let [!held (atom [(e/snapshot v) nil])]
     (set-held! !held v down?)
     (e/watch !held))))

(e/def stage nil)
(e/def commit! (constantly nil))
(e/def discard! (constantly nil))

(e/defn Pulse [v] ; emit [v Ack] for every new `v`, emit nil after ack is called. Ack is a serializable e/fn (in v3)
  (let [[v' release!] (AutoLatch. v)]
    (when release!
      [v' (e/fn [& [x]] (when release! (release!)) x)])))

(defn flash! [!ref v] (reset! !ref v) (reset! !ref nil))

;; commit!* moved out due to v2 compiler bug on self-recursive cc/fn
(let [commit!* (fn rec ([!stage !final] (rec !stage !final @!stage)) ([!stage !final x] (flash! !final x) x))]
  (e/defn Stage
    [Body]
    (let [!stage (atom nil)
          !final (atom nil)]
      (binding [stage    (e/watch !stage)
                commit!  (partial commit!* !stage !final)
                discard! (fn [& _] (reset! !stage nil))]
        (reset! !stage (Body.))
        (e/watch !final)))))

(e/defn Filter [pred v] ; continuous time filter. (e.g. (Filter. some? v) will drop nils from v
  (e/with-cycle [ret (e/snapshot v)]
    (if (pred v) v ret)))

(e/defn Capture [[v Ack]] ; Take a [v ack] pair emitted by Pulse and latch on the Pulsed value, then call ack.
  (let [latched-v (Filter. some? v)]
    (when (and (= v latched-v) Ack) (Ack.))
    latched-v))

(e/defn Electronics []
  (e/client
    (dom/h1 (dom/text "Electronics"))
    (dom/h2 (dom/text "Event Listener"))
    (let [v (dom/input
              (new EventListener "input" #(.. % -target -value)))]
      (dom/pre (dom/text v)))

    (dom/h2 (dom/text "Sampler"))
    (dom/p (dom/text "Snapshot a value on sample."))
    (let [sampled
          (Sampler.
            (e/fn [sample!]
              (let [v (dom/input (new EventListener "input" #(.. % -target -value)))]
                (dom/button (dom/text "sample") (EventListener. "click" sample!))
                (dom/pre (dom/text (contrib.str/pprint-str {:input-value v})))
                v)))]
      (dom/pre (dom/text (contrib.str/pprint-str {:sampled sampled}))))

    (dom/h2 (dom/text "FlipFlop"))
    (dom/p (dom/text "Turns true for each seen value. Stays true until released."))
    (let [v        (dom/input
                     (new EventListener "input" #(.. % -target -value)))
          release! (FlipFlop. v)]
      (dom/button (dom/text "Release!")
                  (new EventListener "click" (fn [_] (when release! (release!)))))
      (dom/pre (dom/text (contrib.str/pprint-str {:input-value v, :flipflop (some? release!)}))))

    (dom/h2 (dom/text "Latch"))
    (dom/p (dom/text "Value passes through unless SET."))
    (let [v (Latch. (e/fn [set! release!]
                      (let [v (dom/input (new EventListener "input" #(.. % -target -value)))]
                        (dom/button (dom/text "SET") (new EventListener "click" set!))
                        (dom/button (dom/text "RELEASE") (new EventListener "click" release!))
                        (dom/pre (dom/text "input value: " v))
                        v)))]
      (dom/pre (dom/text "latched value: " v)))

    (dom/h2 (dom/text "AutoLatch"))
    (dom/p (dom/text "Automatically latch next value. Use case: hold onto a DOM event, drop all next events until we a ready to process the next one."))
    (let [v                    (dom/input (new EventListener "input" #(.. % -target -value)))
          [latched-v release!] (AutoLatch. v)]
      (dom/button (dom/text "RELEASE")
                  (new EventListener "click" (fn [_] (when release! (release!)))))
      (dom/pre (dom/text (contrib.str/pprint-str {:input-value v, :latched-value latched-v}))))

    (dom/h2 (dom/text "Stage"))
    (let [committed
          (Filter. some?
            (Stage.
              (e/fn []
                (let [v (dom/input
                          (set! (.-value dom/node) stage)
                          (EventListener. "input" #(.. % -target -value)))]
                  (dom/button (dom/text "commit!") (EventListener. "click" #(commit!)))
                  (dom/button (dom/text "discard!") (EventListener. "click" #(discard!)))
                  (dom/pre (dom/text (contrib.str/pprint-str {:stage stage})))
                  v))))]
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed}))))

    (dom/h2 (dom/text "Rollback"))
    (let [committed (e/with-cycle [authoritative "authoritative"]
                      (Filter. some?
                        (Stage.
                          (e/fn []
                            (let [value (dom/input
                                          (set! (.-value dom/node) (or stage authoritative))
                                          (EventListener. "input" #(.. % -target -value)))]
                              (dom/button (dom/text "commit!") (EventListener. "click" #(commit!)))
                              (dom/button (dom/text "discard!") (EventListener. "click" #(discard!)))
                              (dom/pre (dom/text (contrib.str/pprint-str {:stage stage})))
                              value)))))]
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed}))))

    (dom/h2 (dom/text "Transactional transfer"))
    (dom/p (dom/text "Ensure a value transfers between two points in the DAG, in continuous time"))
    (dom/p (dom/text "Pulse take a value and return [v ack]. Capture will call ack when it sees v. Pulse then return nil."))
    (let [committed
          (Stage.
            (e/fn []
              (let [v (dom/input
                        (set! (.-value dom/node) stage)
                        (EventListener. "input" #(.. % -target -value)))]
                (dom/button (dom/text "commit!") (EventListener. "click" #(commit!)))
                (dom/button (dom/text "discard!") (EventListener. "click" #(discard!)))
                (dom/pre (dom/text (contrib.str/pprint-str {:stage stage})))
                v)))
          pulse    (Pulse. committed)
          captured (Capture. pulse)]
      (contrib.debug/dbg pulse)
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed, :captured captured}))))))
