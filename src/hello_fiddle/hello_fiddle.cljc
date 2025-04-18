(ns hello-fiddle.hello-fiddle
  (:require [hyperfiddle.electric3 :as e]
            [hyperfiddle.electric-dom3 :as dom]
            [hyperfiddle.router4 :as r]
            [hyperfiddle.electric-forms5 :as forms]))

(defn now-ms []
  #?(:clj (System/currentTimeMillis)
     :cljs (.getTime (new js/Date))))

(e/defn Latch [x]
  (let [!prev (atom (e/Snapshot x))]
    (swap! !prev (fn [old new] (if-not (= new ::killed) new old)) x)
    (e/watch !prev)))

#?(:cljs
   (defn format-duration [ms]
     (cond
       (< ms 1000) (str ms "ms")
       (< ms 10000) (-> (/ ms 100) (Math/floor) (/ 10) (.toFixed 1) (str "s"))
       () (str (Math/round (/ ms 1000)) "s"))))

(e/defn Timing [nm f]
  (let [!f (atom (e/Snapshot f))
        f' (e/watch !f)
        start ((fn [_] (now-ms)) f)
        v (e/Offload-reset f'  #_#_#_ m/blk timeout-ms [::timeout timeout-v])
        done? (e/Some? v)
        end (e/Reconcile (if done? ((fn [_ _] (now-ms)) start v) (e/System-time-ms)))
        duration (- end start)
        !killed (atom false)
        killed? (e/watch !killed)]
    (swap! !killed (constantly false) start)
    (reset! !f f)
    (e/client
      (when-let [e (forms/Button :label "×" :class "cancel" :disabled done?)]
        (let [h (hash e)]
          (e/server
            (swap! !f (constantly (constantly ::killed)) h)
            (swap! !killed (constantly true) h)))))
    (when f (dom/props {:data-timing-label nm
                        :data-timing-start start
                        :data-timing-duration (e/client (format-duration duration))
                        :data-timing- (e/client (format-duration duration))
                        :data-timing-killed killed?
                        :data-timing-done done?
                        }))
    (Latch v)))

(declare css)

(e/defn Scratch []
  (e/client
    (dom/h1 (dom/text "Hello world"))
    (dom/style (dom/text css))
    (let [input (forms/Input* 0 :type :number)]
      (dom/pre
        (dom/text "value is " (e/server (Timing 'task-label #(do (Thread/sleep 2000) input))))))))


(def css
"

pre[data-timing-label]{
  display: block;
  width: fit-content;
  margin: 2rem 0;
}

[data-timing-label]{--color: orange;}
[data-timing-label][data-timing-killed=true]{--color: crimson;}
[data-timing-label][data-timing-done=true][data-timing-killed=false]{--color: green;}

[data-timing-label]{ position: relative; border: 1px var(--color) solid;}
[data-timing-label]::before{
  display: block;
  background-color: var(--color);
  color: white;
  content: attr(data-timing-label) \" \" attr(data-timing-duration);
  padding: 0.2em 0.5em 0.1em 0.5em;
  font-size: 0.75em;
  position: absolute;
  z-index: 1;
  bottom: 100%;
  left: 1em;
  margin: auto;
  border-top-right-radius: 3px;
}

[data-timing-label] button.cancel{
  position: absolute;
  z-index: 2;
  bottom: 100%;
  left: -1px;
  width: 1.1em;
  height: 1.1em;
  padding: 0;
  line-height: 0;
  border-radius: 0;
  border: none;
  border-top-left-radius: 3px;
  border-bottom: 1px var(--color) solid;
  background-color: var(--color);
  color: white;

}


[data-timing-label] button.cancel:hover{
  background-color: #ffbf00;
  cursor: pointer;
}

[data-timing-label] button.cancel:disabled{
  color: gray;
}
")


;; Dev entrypoint
;; Entries will be listed on the dev index page (http://localhost:8080)
(e/defn Fiddles []
  {`Scratch Scratch})

;; Prod entrypoint, called by `prod.clj`
(e/defn ProdMain [ring-req]
  (e/client
    (binding [dom/node js/document.body
              e/http-request (e/server ring-req)]
      (dom/div ; mandatory wrapper div - https://github.com/hyperfiddle/electric/issues/74
        (r/router (r/HTML5-History)
          (Scratch))))))