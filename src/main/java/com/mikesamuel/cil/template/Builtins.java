package com.mikesamuel.cil.template;

import java.util.List;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

final class Builtins {

  private static final
  ImmutableMap<String, Function<List<? extends Object>, Object>> BUILTINS =
      ImmutableMap.<String, Function<List<? extends Object>, Object>>builder()
      .put(
          "cons",
          new Function<List<? extends Object>, Object>() {

            @Override
            public Object apply(List<? extends Object> actuals) {
              int n = actuals.size();
              if (n == 0) {
                return ImmutableList.of();
              }
              ImmutableList.Builder<Object> b = ImmutableList.builder();
              for (int i = 0; i < n - 1; ++i) {
                b.add(actuals.get(i));
              }
              Iterable<?> ls = (Iterable<?>) actuals.get(n - 1);
              b.addAll(ls);
              return b.build();
            }

          })
      .build();

  public static
  Function<List<? extends Object>, Object> getBuiltin(String methodName) {
    return BUILTINS.get(methodName);
  }

}
