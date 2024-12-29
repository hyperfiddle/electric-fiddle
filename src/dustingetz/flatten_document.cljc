(ns dustingetz.flatten-document)

(defn flatten-nested ; claude generated this
  "Flattens a nested data structure into a sequence of maps with :tab, :name, and :value keys.
   For collection items, uses simple index numbers.
   - data: The nested data structure to flatten
   - indent: The current indentation level (default: 0)"
  ([data]
   (flatten-nested data 0))
  ([data indent]
   (cond
     ;; Handle maps
     (map? data)
     (mapcat (fn [[k v]]
               (let [node {:tab indent :name k}]
                 (cond
                   ;; For maps and sequences, emit parent node and recurse
                   (map? v)
                   (cons node (flatten-nested v (inc indent)))

                   (sequential? v)
                   [{:tab indent :name k :value '...}]
                   #_(cons node (flatten-nested v (inc indent)))

                   ;; For nil values, just emit the node
                   (nil? v)
                   [node]

                   ;; For other values, include the value
                   :else
                   [(assoc node :value v)])))
       data)

     ;; Handle sequences
     (sequential? data)
     (mapcat (fn [i v]
               (cond
                 (or (map? v) (sequential? v))
                 (cons {:tab indent :name i}
                   (flatten-nested v (inc indent)))

                 ()
                 [{:tab indent :name i :value v}]))
       (range)
       data)

     ;; Handle leaf values (shouldn't normally hit this case)
     :else
     [{:tab indent :value data}])))

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
  )