### Clojure で「マルバツ」作って、いろいろ学べたら、と。

いまのところ、以下のような見た目。

marubatsu.core=> (play3)

```
    A B C
  # # # #
1 # . . .
2 # . . .
3 # . . .
```

Enter [ q, u, a1 - c3 ]> b2

```
    A B C
  # # # #
1 # . . .
2 # . 0 .
3 # . . .

[ computer's turn ]

    A B C
  # # # #
1 # . . .
2 # . 0 .
3 # . . X
```

Enter [ q, u, a1 - c3 ]>