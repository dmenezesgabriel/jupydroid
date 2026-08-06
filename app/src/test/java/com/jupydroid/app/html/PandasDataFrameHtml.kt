package com.jupydroid.app.html

/**
 * A real `pandas.DataFrame._repr_html_()`, captured rather than written from memory:
 *
 * ```
 * pd.DataFrame({'a': [1, 2], 'b': ['x', 'y']})._repr_html_()
 * ```
 *
 * Three things in it are the reason the fixture is verbatim, and all three are easy to get
 * wrong from imagination: the `<style scoped>` block whose CSS must never reach the screen,
 * the empty leading `<th></th>` that gives the index column its header, and the row index
 * arriving as a `<th>` inside `<tbody>` rather than as a `<td>`.
 */
const val PANDAS_DATAFRAME_HTML: String = """<div>
<style scoped>
    .dataframe tbody tr th:only-of-type {
        vertical-align: middle;
    }

    .dataframe tbody tr th {
        vertical-align: top;
    }

    .dataframe thead th {
        text-align: right;
    }
</style>
<table border="1" class="dataframe">
  <thead>
    <tr style="text-align: right;">
      <th></th>
      <th>a</th>
      <th>b</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <th>0</th>
      <td>1</td>
      <td>x</td>
    </tr>
    <tr>
      <th>1</th>
      <td>2</td>
      <td>y</td>
    </tr>
  </tbody>
</table>
</div>"""
