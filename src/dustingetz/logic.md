# Logic

Key difference with a miniKanren-like system: no backtracking.
Backtracking allows for sequential exploration of the search space. If a combination doesn’t hold, the system rolls back and tries another combination. This is memory-efficient, as the search space could be infinite. We sometimes care only about one answer, sometimes N, and sometimes all posible answers (potentially infinite). Also there are multiple ways to explore a search space, like there are multiple ways to traverse a tree.
In Electric, all potential branches are mounted. There is no backtracking, instead we don’t collect branches with no answer (as per (e/amb)). The larger the search space, the larger the DAG.
Performances aside, both approaches seems equivalent AFAICT.
I guess we could implement a variant of e/for mounting only the necessary branches. It would:
mount up to N branches (as requested by the parent if the parent asks for up to 1, 2 or n answers)
prune (unmount) branches returning (e/amb)
allowing multiple traversal strategies