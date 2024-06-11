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

#?(:cljs (defn with-listener
           ([n e f] (with-listener n e f nil))
           ([n e f o] (.addEventListener n e f o) #(.removeEventListener n e f o))))

#?(:cljs
   (defn listen "Takes the same arguments as `addEventListener` and returns an uninitialized
  missionary flow that handles the listener's lifecycle producing `(f e)`.
  Relieves backpressure. `opts` can be a clojure map."
     ([node event-type] (listen node event-type identity))
     ([node event-type f] (listen node event-type f {}))
     ([node event-type f opts]
      (->> (m/observe (fn [!] (with-listener node event-type #(! (f %)) (clj->js opts))))
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

;; D: is this a relieved m/observe?
(e/defn Sampler [Body] ; used by Stage
  (let [!sampled (atom nil)
        !samplee (atom nil)]
    (reset! !samplee (Body. (fn ([] (reset! !sampled @!samplee) nil) ([x] (reset! !sampled x) x))))
    (e/watch !sampled)))

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

;; TODO documentation
;;
;; @Peter:
;; - start with the DFlipFlop. It is the actual thing that exists in the world
;;   and FlipFlop is just an optimization of it. We explain how it
;;   snapshots/latches the input value until reset is called. We show code how
;;   to use it on an event listener like the click(s) on a pay button.
;; - Next we explain that in many cases we don't care about the latched value,
;;   we actually want to use the latest value. For that case there's a simpler
;;   FlipFlop that returns only the reset fn.
;; - Next we explain the flip flops interpret the input signal. Non-nil values
;;   trigger the flip flop. A user now might ask "what if I need to trigger on
;;   nil values too?" or "what if I want to trigger on all changes?". Here we
;;   show the optional up? argument and say to e.g. react on all changes you can
;;   pass (constantly true). This is more intuitive than (constantly false),
;;   which we'd need to use if we keep the down? fn

(let [->done-fn (fn [!held] (fn f ([] (f nil)) ([ret] (swap! !held assoc 1 nil) ret)))
      step      (fn [!held v up?]
                  (let [[_ done! :as held] @!held]
                    (if (or done! (not (up? v)))
                      held
                      (compare-and-set! !held held [v (->done-fn !held)]))))]
  (e/defn DFlipFlop
    "Takes a continuous time value `v` and toggle as soon as `v` changes. Toggles
immedately on mount. On toggle, latch on the current value of `v`, returning
`[latched-v release!]`. `release!` is a reset function, releasing the latch.
When `release!` is called, the FlipFlop toggles and return [last-latched-v nil],
until `v` changes again, at which point `[new-latched-v release!]` is returned."
    ([v] (new DFlipFlop v some?))
    ([v up?] (let [!held (atom [nil nil])] (step !held v up?) (e/watch !held)))))

(e/defn FlipFlop "A toggle. Toggles up when `control` changes, returning a
\"reset\" function. Calling this \"reset\" function toggles the flip-flop down,
at which point the flip-flop returns nil, until `control` changes again. Similar
to DFlipFlop, but without an output signal. Use case: track effect completion on
a value in continuous time, when we don't care about the value."
  ([control] (new FlipFlop control some?))
  ([control up?]
   (second (DFlipFlop. control up?))))

;; Alternative FlipFlop impl
#_(let [->done-fn (fn [!done!] (fn f ([] (f nil)) ([ret] (reset! !done! nil) ret)))
      step      (fn [!done! v up?] (when (up? v) (compare-and-set! !done! nil (->done-fn !done!))))]
  (e/defn FlipFlop
    ([v]     (new FlipFlop v some?))
    ([v up?] (let [!done! (atom nil)] (step !done! v up?) (e/watch !done!)))))

(e/def stage nil)
(e/def commit! (constantly nil))
(e/def discard! (constantly nil))

(e/defn Pulse [v] ; emit [v Ack] for every new `v`, emit nil after ack is called. Ack is a serializable e/fn (in v3)
  (let [[v' release!] (DFlipFlop. v)] ; P: Why isn't DFlipFlop enough? G: because release! isn't serializable, e/fn is in v3.
    ;; NOTE P: release! should probably always be called on the client, and v3
    ;; doesn't transfer client-only values (unlike v2), so there might not be a
    ;; need for an e/fn wrapper. Pulse can be removed if true.
    (when release!
      [v' (e/fn [& [x]] (when release! (release!)) x)] ; NOTE e/fn is not colored so calling it on the server would call release! on the wrong peer.
      )))

;; Leo: Beware, this work in an event handler but not in continuous time.
;;      because in an event listener, reset! v will trigger a new propagation
;;      turn, Then reset! nil will trigger another one. But in continuous time,
;;      reset! v will update the atom and schedule a new propagation turn. then
;;      reset to nil. So on the next propagation we'll see nil. Multiple reset!
;;      on the same ref in a propagation turn cause a Last-Write-Wins behavior.
;; HACK
(defn flash! [!ref v] (reset! !ref v) (reset! !ref nil))

;; D: is Stage the right name? What about Cell?
;; commit!* moved out due to v2 compiler bug on self-recursive cc/fn
(let [commit!* (fn rec
                 ([!stage !final] (rec !stage !final @!stage))
                 ([!stage !final x] (flash! !final x) x))]
  (e/defn Stage
    [Body]
    (let [!stage (atom nil)
          !final (atom nil)]
      (binding [stage    (e/watch !stage)
                commit!  (partial commit!* !stage !final)
                discard! (fn [& _] (reset! !stage nil))]
        (reset! !stage (Body.))
        (e/watch !final)))))

;; FIXME Leo: could introduce glitches. Maybe use (m/eduction (filter…)…) instead. Beware of pendings.
(e/defn Filter [pred v] ; continuous time filter. (e.g. (Filter. some? v) will drop nils from v
  (e/with-cycle [ret (e/snapshot v)]
    (if (pred v) v ret)))

(e/defn Capture [[v Ack]] ; Take a [v ack] pair emitted by Pulse and latch on the Pulsed value, then call ack.
  (let [latched-v (Filter. some? v)]
    (when (and (= v latched-v) Ack) (Ack.))
    latched-v))

#?(:cljs
   (defn fork [n flow]
     (m/ap
       (let [!id (atom 0), !ret (atom (sorted-map))]
         (m/amb= (m/?> (m/watch !ret))
           (do (when-some [v (m/?> flow)]
                 (swap! !ret (fn [ret]
                               (let [id (swap! !id inc), ret (assoc ret id [v #(swap! !ret dissoc id)])]
                                 (apply dissoc ret (take (- (count ret) n) (keys ret)))))))
               (m/amb)))))))

#?(:cljs (defn fork-events [n node event-type f opts] (fork n (listen node event-type f opts))))

(e/defn ForkingEventListener
  ([event-type]             (new ForkingEventListener          event-type identity))
  ([event-type f]           (new ForkingEventListener dom/node event-type f))
  ([node event-type f]      (new ForkingEventListener node     event-type f        {}))
  ([node event-type f opts] (new ForkingEventListener node     event-type f        opts ##Inf))
  ([node event-type f opts concurrency-factor]
   (e/client (new (fork-events concurrency-factor node event-type f opts)))))


(e/defn Electronics []
  (e/client
    (dom/h1 (dom/text "Electronics"))
    (dom/h2 (dom/text "Event Listener"))
    (let [v (dom/input
              ;; NOTE Leo: I think passing an initial value should be mandatory.
              ;; E.g. EventListener returns nil but input contains "", but
              ;; depends on use case.
              (new EventListener "input" #(.. % -target -value)))]
      (dom/pre (dom/text v)))

    (dom/h2 (dom/text "Sampler"))
    (dom/p (dom/text "Snapshot a value on sample."))
    (let [sampled
          (Sampler.
            (e/fn [sample!]
              (let [v (dom/input (new EventListener "input" #(.. % -target -value)))]
                (dom/button (dom/text "sample") (EventListener. "click" #(sample!)))
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
      (dom/pre (dom/text "output: " v)))

    (dom/h2 (dom/text "DFlipFlop"))
    (dom/p (dom/text "Automatically latch next value. Use case: hold onto a DOM event, drop all next events until we are ready to process the next one."))
    (let [v                    (dom/input (new EventListener "input" #(.. % -target -value)))
          [latched-v release!] (DFlipFlop. v)]
      (dom/button (dom/text "RELEASE")
                  (new EventListener "click" (fn [_] (when release! (release!)))))
      (dom/pre (dom/text (contrib.str/pprint-str {:input-value v, :latched-value latched-v, :flipflop (some? release!)}))))


(comment
  (let [v "hello"
        [latched-v release!] (DFlipFlop. v)
        [latched-v release!2] [latched-v (fn [] (fn [] (release!)))]]))

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
          pulse    (Pulse. committed) ; Pulse :: v -> [latched-v Ack]
          captured (Capture. pulse)] ; Capture :: [latched-v Ack] -> latched-v
      (contrib.debug/dbg pulse)
      (dom/pre (dom/text (contrib.str/pprint-str {:committed committed, :captured captured}))))))
