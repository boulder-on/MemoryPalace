# MemoryPalace
Java List-like classes for primitives with dynamic memory compression. These
classes were created with the iea of working with very large arrays in Java.
There are 3 main benefits to using these classes over Java Lists or arrays
directly

- Holds > 2 GB of data
- Does not require Autoboxing
- Automatically compresses sub-sections of the data

## How does it work?

The provided classes are all intended to very closely match the API
of the java.util.List class. Internally, rather than using a single
large object array, the data is stored in chunks. By storing the data
in chunks it is possible to compresses the least-recently-used chunks
and it's possible to address more than a Java int (i.e. > 2 billion items).

As you are interacting with these lists, there are a certain number that
are allowed to be decompressed at a time. If a chunk is not the most recently
used then it may be compressed.

Compression is done using the deflate algorithm that is standard to ZIP files.

## Example

``` Java
    var doubleList = new ListOfDouble(1000);
    for (int n = 0; n < 10_000_000; ++n)
        doubleList.add(n);

    var value = doubleList.get(5_000_000);
    
```

## Configurable parameters

- Chunk size - The size of any given sub array. Default 128
- Overload factor - If elements are inserted then this determines the maximum size a chunk can be grown to before it splits. Default 1.5. Valid values 1-2.
- Allowed decompressed count - The number of chunks that can be in a decompressed state at a time. Default 2.

The above constructor parameters will likely all require some amount of tuning
to get the best performance for your data.

## Limitations

The code is currently written for Java 11. It could likely be back ported
relatively easily to earlier versions.

## Future ideas

- Pluggable compression algorithms

There is no best compression algorithm for all data. It would be good if
a user could test to find the best (smallest size, fastest) compression
for their data.

- Add JMH tests.

Performance comparison vs ArrayList objects. This is somewhat tricky as the
data that's added to the list greatly affects compression time and size.