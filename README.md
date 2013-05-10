# panoptic

__panoptic__ monitors single files or whole directories for changes.

[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

## Usage

```clojure
(use 'panoptic.core)

(def log-watcher
  (-> (simple-file-watcher ["log.txt" "errors.txt"])
    (on-create #(println (:path %) "was created."))
    (on-delete #(println (:path %) "was deleted."))
    (on-modify #(println (:path %) "was modified."))
    (start-watcher!)))

...

(stop-watcher! log-watcher)
```

FIXME

## License

Copyright &copy; 2013 Yannick Scherer

Distributed under the Eclipse Public License, the same as Clojure.
