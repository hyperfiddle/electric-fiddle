(ns dustingetz.flatten-document)

;; flatten-nested vs explorer-seq:
;; - flatten-nested will treat sequables (seqs, vectors, sets, ...)
;;   - as plain values if they are nested under a map (e.g. #{:a :b})
;;   - as an indexed tree in the sequable is the root value (e.g. '([[0] :a], [[1] :b]))
;; - explorer-seq will treat all sequables as an indexed tree, nested or not.

(defn flatten-nested ; claude generated this
  ([data] (flatten-nested data []))
  ([data path]
   (cond
     (map? data)
     (mapcat (fn [[k v]]
               (cond
                 (or (map? v) (set? v))
                 (cons {:path path :name k} (flatten-nested v (conj path k)))

                 ; render collections of records as hyperlinks
                 (and (sequential? v) (map? (first v)))
                 [{:path path :name k :value (constantly v)}] ; lift

                 ; render simple collections inline
                 (and (sequential? v) (not (map? (first v))))
                 [{:path path :name k :value v}]

                 #_#_(nil? v) [{:path path :name k}]
                 () [{:path path :name k :value v}]))
       data)

     ; render simple collections as indexed maps – only if simple collection is the root value
     (or (sequential? data) (set? data))
     (mapcat (fn [i v]
               (cond
                 (or (map? v) (sequential? v))
                 (cons {:path path :name i}
                   (flatten-nested v (conj path i)))

                 ()
                 [{:path path :name i :value v}]))
       (range) data)

     ; what else?
     () [{:path path :value data}])))

(comment
  (def test-data
    '{:response
      {:Owner
       {:DisplayName string
        :ID string}
       :Grants
       {:seq-of
        {:Grantee
         {:DisplayName string
          :EmailAddress string
          :ID string
          :Type [:one-of ["CanonicalUser" "AmazonCustomerByEmail" "Group"]]
          :URI string}
         :Permission [:one-of ["FULL_CONTROL" "WRITE" "WRITE_ACP" "READ" "READ_ACP"]]}}}})
  (flatten-nested test-data)
  := '({:path [], :name :response}
       {:path [:response], :name :Owner}
       {:path [:response :Owner], :name :DisplayName, :value string}
       {:path [:response :Owner], :name :ID, :value string}
       {:path [:response], :name :Grants}
       {:path [:response :Grants], :name :seq-of}
       {:path [:response :Grants :seq-of], :name :Grantee}
       {:path [:response :Grants :seq-of :Grantee],
        :name :DisplayName,
        :value string}
       {:path [:response :Grants :seq-of :Grantee],
        :name :EmailAddress,
        :value string}
       {:path [:response :Grants :seq-of :Grantee], :name :ID, :value string}
       {:path [:response :Grants :seq-of :Grantee],
        :name :Type,
        :value [:one-of ["CanonicalUser" "AmazonCustomerByEmail" "Group"]]}
       {:path [:response :Grants :seq-of :Grantee], :name :URI, :value string}
       {:path [:response :Grants :seq-of],
        :name :Permission,
        :value [:one-of ["FULL_CONTROL" "WRITE" "WRITE_ACP" "READ" "READ_ACP"]]})

  )

;; -------
