# e/amb part 2 — narrowing and nondeterminism

### SICP connection

> SICP: The key idea here is that expressions in a **nondeterministic** language can have more than one possible value. ... Abstractly, we can imagine that evaluating an amb expression causes time to split into branches, where the computation continues on each branch with one of the possible values of the expression.

Yes, Electric's `e/amb` models "nondeterminism"† like in SICP, in the sense that `(inc (e/amb 1 2))` evaluates to more than one result:

```
; from SICP 4.3.1
(list (e/amb 1 2 3) (e/amb :a :b))
; (1 :a) (1 :b) (2 :a) (2 :b) (3 :a) (3 :b)
```

If you try to run this example, note that because of how electric-dom works, you'll need to materialize with `e/as-vec` to see it:
```
(dom/p (dom/text (pr-str (e/as-vec (list (e/amb 1 2 3) (e/amb :a :b))))))
; "[(1 :a) (1 :b) (2 :a) (2 :b) (3 :a) (3 :b)]", in the DOM
```
Without `e/as-vec`, you'll get 6 concurrent point writes to the same dom textnode, i.e. the writes will race and you'll see only the last one.

### Advanced

Here is SICP's `prime-sum-pair`, which actually demonstrates an issue with Electric's if semantics (backlog):

```
(e/defn Prime-sum-pair [as bs]
  (e/for [a as, b bs] ; why the e/for, why not operate on tables directly?
    (if (prime? (+ a b))
      [a b]
      (e/amb))))

(Prime-sum-pair (e/amb 1 3 5 8) (e/amb 20 35 110))
; [3 20] [3 110] [8 35]
```
The problem here is that we want to write it as simply
```
(e/defn Prime-sum-pair [as bs]
  (if (prime? (+ as bs))
    [as bs]
    (e/amb)))
```
where `+`, `prime?`, `if` etc are auto-mapped. The problem is that our `if` primitive does not perform *narrowing*. We wish the truthy branch to only see values of `as` and `bs` that passed the predicate, but we did not implement such narrowing semantics yet. So, we workaround by using `e/for` to narrow each table down to a singular value before calling `if` in a way that doesn't depend on if narrowing. Future work: implement `if` narrowing and bring inline with Verse/SICP semantics!

(For this reason, I recommend you mostly ignore the auto-mapping functionality of Electric, just use `e/for` to iterate over tables as singular values, and your if statements will be happy.)
