# panoptic

__panoptic__ monitors single files or whole directories for changes.

[![endorse](https://api.coderwall.com/xsc/endorsecount.png)](https://coderwall.com/xsc)

This is only for playing around for now. But much more functionality can be expected.

## Usage

__Leiningen__ ([via Clojars](https://clojars.org/panoptic))

```clojure
[panoptic "0.1.0-SNAPSHOT"]
```

__REPL__

```clojure
(use 'panoptic.core)

(def files
  (-> (observable-files ["test.txt"])
    (on-create #(println (:path %) "was created."))
    (on-delete #(println (:path %) "was deleted."))
    (on-modify #(println (:path %) "was modified."))))

(def watcher
  (-> (simple-file-watcher files :interval 55 :checker md5)
    (start-watcher!)))

...

(stop-watcher! watcher)
```

## License

Copyright &copy; 2013 Yannick Scherer

Distributed under the Eclipse Public License, the same as Clojure.
