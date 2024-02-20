(ns datagrid.parser
  (:require [clojure.spec.alpha :as s]
            [clojure.string :as str]))

(def WHITE-SPACES #"[\p{Zs}\s]*") ; \p{Zs} means all space-like chars (e.g. NBSP, list: https://jkorpela.fi/chars/spaces.html)
(defn blank-string? [str] (and (string? str) (boolean (re-matches WHITE-SPACES str))))
(defn read-tokens [str] (map first (re-seq #"(\S+)|([\p{Zs}\s]+)" str))) ; \S is equivalent to [^\s]
(defn read-lines [str]
  (let [lines (str/split-lines str)]
    (if-let [eof (re-find #"\r?\n+$" str)]
      (conj lines (str/replace eof #"\n+$" ""))
      lines)))

(s/def ::blank blank-string?)
(s/def ::token (s/and string? #(re-matches #"\S+" %)))
(s/def ::ip ::token)
(s/def ::alias (s/or :hostname ::token, :blank ::blank))
(s/def ::comment (s/and string? #(str/starts-with? % "#")))

(defn conform-entry [value] (s/conform (s/cat :ip ::ip, :aliases (s/* ::alias)) (read-tokens value)))
(defn unform-entry [{:keys [ip aliases]}] (str ip (str/join "" (map second aliases))))
(s/def ::entry (s/conformer conform-entry unform-entry))

(s/def ::line (s/or :blank ::blank, :comment ::comment, :entry ::entry))
(s/def ::file (s/coll-of ::line))

(comment
  (s/conform ::file (read-lines "# hostsfile\n::1 local  local2\n"))
  (s/conform ::file (read-lines "# hostsfile\n::1\tlocal"))
  (s/conform ::file (read-lines "# hostsfile\n# other comment\n   \n192.168.1.0 localhost   local\n::1 localhost local6"))
  (s/conform ::file (read-lines "# hostsfile\n# other comment\n   \n\n192.168.1.0 localhost   local\n::1"))
  (s/conform ::file (read-lines (slurp "/etc/hosts")))
  (= 
    (prn (slurp "/etc/hosts"))
    (prn (str/join "\n" (s/unform ::file (s/conform ::file (read-lines (slurp "/etc/hosts")))))
      ))

  (map read-tokens (read-lines "# hostsfile\n# other  comment\n"))

  (re-find #"(\p{Zs}|\s)*" "     \t")
  (re-find #"\s+" "     ")
  )

(defn parse [hosts-file-content-str] (s/conform ::file (read-lines hosts-file-content-str)))
(defn parse-line [str] (s/conform ::line str)) ; utility function, not used by `parse`
(defn serialize [ast] (str/join "\n" (s/unform ::file ast)))
(defn serialize-line [line-ast] (s/unform ::line line-ast)) ; utility function

(comment
  (= (slurp "/etc/hosts") (serialize (parse (slurp "/etc/hosts"))))
  := true
  )

(defn entries [ast]
  (->> ast
    (map-indexed vector)
    (filter #(= :entry (first (second %))))
    (map (fn [[line-number [_ entry]]]
           (assoc entry :line line-number)))))

(comment
  (entries (parse (slurp "/etc/hosts")))
  )


(comment
  (serialize
 [[6
   [:entry
    {:ip "127.0.0.1", :aliases [[:blank "\t"] [:hostname "localhost"]]}]]
  [7
   [:entry
    {:ip "255.255.255.255",
     :aliases [[:blank "\t"] [:hostname "broadcasthost"]]}]]
  [8
   [:entry
    {:ip "::1", :aliases [[:blank "             "] [:hostname "localhost"]]}]]
  [11
   [:entry
    {:ip "127.0.0.1",
     :aliases [[:blank " "] [:hostname "kubernetes.docker.internal"]]}]]
  [13
   [:entry
    {:ip "127.0.0.1", :aliases [[:blank "\t"] [:hostname "facebook.com"]]}]]])
  )
