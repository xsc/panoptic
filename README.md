# panoptic

__panoptic__ monitors single files or whole directories for changes.

[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

This is only for playing around for now. But much more functionality can be expected.

## Usage

__Leiningen__

```clojure
[panoptic "0.1.0-SNAPSHOT"]
```

__REPL__

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

## License

Copyright &copy; 2013 Yannick Scherer

Distributed under the Eclipse Public License, the same as Clojure.
