package memorypalace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.LongBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.function.*;
import java.util.stream.LongStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * This class is modeled off a java.util.List object, but is instead used to carry float primitives.
 * The list is also dynamically self compressing. A ListOfLong has a chunkSize to dictate the size
 * of sub-sections of the list. When there are enough sub-sections, the least-recently-used sub-sections
 * are compressed. The compression is done using the deflate algorithm. Compressed sections are held
 * in memory as byte arrays until they are needed again. When a compressed section is required it is
 * automatically decompressed.
 */
public class ListOfLong {
    /** The default number of elements in any given chunk. **/
    private final int chunkSize;

    /** The number of chunks that are allowed to be in an uncompressed state at any given time. **/
    private final int allowedDecompressedCount;

    /** The multiplier for the largest chunk size allowed before adding another element splits the chunk in 2. **/
    private final double overloadFactor;

    /** The number of elements overall in the List. **/
    private long elementCount = 0;

    /** The list of chunks that are currently not compressed. **/
    private final ArrayList<Chunk> decompressedChunks = new ArrayList<>();

    /** All of the chunks in the List. **/
    private final ArrayList<Chunk> chunks = new ArrayList<>();

    private static final long BYTE_COUNT = Long.BYTES;

    public ListOfLong() {
        this(128, 1.5, 2);
    }

    public ListOfLong(int chunkSize) {
        this(chunkSize, 1.5, 2);
    }

    /**
     *
     * @param chunkSize The size of a sub-section of the list
     * @param overloadFactor The factor by which a sub-section can be larger than chunksize (used when inserting)
     * @param allowedDecompressedCount The number of sub-sections that can be in a decompressed state at any given time.
     * @throws IllegalArgumentException if chuckSize < 10 - that's really defeating the purpose!
     * @throws IllegalArgumentException if overloadFactor is < 1 or > 2
     * @throws IllegalArgumentException if allowedDecompressionCount < 2.
     */
    public ListOfLong(int chunkSize, double overloadFactor, int allowedDecompressedCount) {
        if (chunkSize < 10)
            throw new IllegalArgumentException("What are you doing? A tiny chunk size like that is useless.");
        if (overloadFactor < 1 || overloadFactor > 2)
            throw new IllegalArgumentException("Overload factor must be (1, 2)");
        if (allowedDecompressedCount < 2)
            throw new IllegalArgumentException("Must be allowed to have at least 2 chunks decompressed at a time");

        this.chunkSize = chunkSize;
        this.overloadFactor = overloadFactor;
        this.allowedDecompressedCount = allowedDecompressedCount;
        chunks.add(new Chunk(0));
    }

    /**
     * Get the count of elements stored.
     * @return The number of elements the object is storing.
     */
    public long size() {
        return elementCount;
    }

    /**
     * Check if the list contains anything.
     * @return true if there is nothing stored.
     */
    public boolean isEmpty()
    {
        return elementCount == 0;
    }

    /**
     * Determine the amount of memory that is being used by arrays this list is holding.
     * This value contains all compressed and uncompressed data.
     * @return The number of bytes of compressed and uncompressed data this list is holding.
     */
    public long usedMemory() {
        return chunks.stream().mapToLong(Chunk::usedMemory).sum();
    }

    /**
     * Determine the amount of memory that the list would be using if there was no compression.
     * @return The total bytes that should be used if there was no compression.
     */
    public long uncompressedSize() {
        return elementCount * BYTE_COUNT;
    }

    /**
     * Add a value to the end of the list.
     * @param v The value to add.
     * @return This list.
     */
    public ListOfLong add(long v) {
        elementCount++;
        var lastChunk = chunks.get(chunks.size() - 1);
        if (!lastChunk.append(v)) {
            var c = new Chunk(lastChunk.nextOffset());
            c.append(v);
            chunks.add(c);
        }
        return this;
    }

    /**
     * Add a sub-section of the given array to the end of the list.
     *
     * @param v The data to add.
     * @param offset The offset to start at in v.
     * @param len The number of elements of v to add.
     * @return This list.
     */
    public ListOfLong addAll(long[] v, int offset, int len) {
        var buff = LongBuffer.wrap(v, offset, len);
        return addAll(buff);
    }

    /**
     * Add all of the elements of the given array to the end of the list.
     * @param v The elements to add.
     * @return This list.
     */
    public ListOfLong addAll(long[] v) {
        return addAll(LongBuffer.wrap(v));
    }

    /**
     * Add all elements of the given buffer from the current position to the limit
     * to the end of the list.
     * @param buff The data to add.
     * @return This list.
     */
    public ListOfLong addAll(LongBuffer buff) {
        elementCount += buff.limit() - buff.position();

        var lastChunk = chunks.get(chunks.size() - 1);
        lastChunk.append(buff);

        while (buff.limit() != buff.position())
        {
            var c = new Chunk(lastChunk.nextOffset());
            c.append(buff);
            chunks.add(c);
            lastChunk = c;
        }

        return this;
    }

    /**
     * Insert the given value at the specified index.
     * @param idx The index to insert at.
     * @param v The value to insert.
     * @return This list.
     */
    public ListOfLong add(int idx, long v) {
        long size = size();
        if (idx < 0 || idx > size + 1)
            throw new IllegalArgumentException("Invalid index " + idx + "on valid [0, " + size + "]");

        //The simple case of adding on the end
        if (idx == size)
        {
            add(v);
            return this;
        }

        elementCount++;
        var addToChunk = getChunk(idx);
        var newChunks = addToChunk.chunk.insert((int)addToChunk.correctedIdx(idx), v);
        //if the chunk needed to be split to accomodate the new data
        if (newChunks.length > 1)
        {
            chunks.set(addToChunk.listIdx, newChunks[0]);
            chunks.add(addToChunk.listIdx + 1, newChunks[1]);
        }
        updateOffsets(addToChunk.listIdx);
        return this;
    }

    /**
     * Get the value at the given index.
     * @param idx The index of the value to read.
     * @return The value at the given index.
     * @throws ArrayIndexOutOfBoundsException if the index is < 0 or >= size()
     */
    public long get(long idx) {
        var size = size();
        if (idx < 0 || idx >= size)
            throw new ArrayIndexOutOfBoundsException("Invalid index " + idx + "on valid [0, " + (size - 1) + "]");

        var p = getChunk(idx);
        return p.chunk.get(p.correctedIdx(idx));
    }

    /**
     * Set the value at the given index.
     * @param idx The index to set the value for.
     * @param v The value to place in the list.
     * @return The previous value at the given index.
     */
    public long set(long idx, long v)
    {
        var size = size();
        if (idx < 0 || idx >= size)
            throw new IllegalArgumentException("Invalid index " + idx + " only [0," + size + "] are valid");

        var p = getChunk(idx);
        return p.chunk.set(p.correctedIdx(idx), v);
    }

    /**
     * Remove the value at the given index.
     * @param idx The index of the value to remove.
     * @return The value at the given index.
     */
    public long remove(long idx)
    {
        if (idx < 0 || idx >= size())
            throw new IllegalArgumentException("Invalid index " + idx + " only [0," + (size() - 1) + "] are valid");
        var pair = getChunk(idx);
        elementCount--;
        var ret = pair.chunk.remove(pair.correctedIdx(idx));
        if (pair.chunk.insertPos == 0)
            chunks.remove(pair.listIdx);
        updateOffsets(pair.listIdx);

        return ret;
    }

    /**
     * Perform an action using each item in the list. This does not change the list.
     *
     * @param action The action to perform on list elements.
     */
    void forEach(Consumer<? super Long> action)
    {
        var i = longIterator();
        while (i.hasNext())
            action.accept(i.next());
    }

    /**
     * Stream the entire contents of the list.
     * @return A stream for the list.
     */
    public LongStream stream() {

        var streamer = new Streamer();
        return LongStream.iterate(get(0), streamer, streamer);
    }

    /**
     * The stream for the list.
     */
    private class Streamer implements LongPredicate, LongUnaryOperator
    {
        final long count = size();
        long idx = 1;
        IteratorLong iterator = longIterator();

        Streamer()
        {
            if (iterator.hasNext())
                iterator.next();
        }

        @Override
        public boolean test(long value) {
            return idx <= count;
        }

        @Override
        public LongPredicate and(LongPredicate other) {
            return LongPredicate.super.and(other);
        }

        @Override
        public LongPredicate negate() {
            return LongPredicate.super.negate();
        }

        @Override
        public LongPredicate or(LongPredicate other) {
            return LongPredicate.super.or(other);
        }

        @Override
        public long applyAsLong(long operand) {
            idx++;
            if (!iterator.hasNext())
                return Long.MAX_VALUE;
            return iterator.next();
        }

        @Override
        public LongUnaryOperator compose(LongUnaryOperator before) {
            return LongUnaryOperator.super.compose(before);
        }

        @Override
        public LongUnaryOperator andThen(LongUnaryOperator after) {
            return LongUnaryOperator.super.andThen(after);
        }
    }


    ListIterator<Long> listIterator()
    {
        return listIterator(0);
    }

    ListIterator<Long> listIterator(int startAt)
    {
        var c = size();
        if (startAt < 0 || startAt > c)
            throw new IllegalArgumentException("Start at must be from 0 to " + c + ", " + startAt + " is invalid");

        return new ListIterator<>() {
            final long count = c;
            long idx = startAt;

            @Override
            public boolean hasNext() {
                return idx < count;
            }

            @Override
            public Long next() {
                return get(idx++);
            }

            @Override
            public boolean hasPrevious() {
                return idx > 0;
            }

            @Override
            public Long previous() {
                return get(--idx);
            }

            @Override
            public int nextIndex() {
                return (int)idx + 1;
            }

            @Override
            public int previousIndex() {
                return (int)idx - 1;
            }

            @Override
            public void remove() {
                ListOfLong.this.remove(idx);
            }

            @Override
            public void set(Long aDouble) {
                ListOfLong.this.set(idx, aDouble);
            }

            @Override
            public void add(Long aDouble) {
                ListOfLong.this.add(aDouble);
            }
        };
    }

    public Iterator<Long> iterator()
    {
        return new Iterator<>() {
            final IteratorLong iterator = longIterator();

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public Long next() {
                return iterator.next();
            }
        };
    }

    /**
     * This version of the iterator avoid Autoboxing.
     * @return An Iterator like object that does not Autobox.
     */
    public IteratorLong longIterator()
    {
        return new IteratorLong() {
            int chunkIdx = 0;
            IteratorLong chunkIter = chunks.get(chunkIdx).floatIterator();

            @Override
            public boolean hasNext() {
                return chunkIter.hasNext();
            }

            @Override
            public long next()
            {
                long ret = chunkIter.next();
                if (!chunkIter.hasNext())
                {
                    ++chunkIdx;
                    if (chunkIdx < chunks.size())
                        chunkIter = chunks.get(chunkIdx).floatIterator();
                }
                return ret;
            }
        };
    }

    public long[] toArray() {
        if (size() > Integer.MAX_VALUE)
            throw new IndexOutOfBoundsException("Cannot create an array this large.");

        var ret = new long[(int)size()];
        var buff = LongBuffer.wrap(ret);

        for (Chunk c : chunks)
            c.getAll(buff);

        return ret;
    }

    /**
     * If you have added or removed items to the middle of the list then the list will be using more memory
     * that absolutely required. This method rebuilds the list a sub-section at a time such that each
     * sub-section again only has chunkSize elements.
     */
    public void trimToSize()
    {
        int firstShrinkable = -1;
        for (int n = 0; n < chunks.size() - 1; ++n)
        {
            var c = chunks.get(n);
            if (c.insertPos != chunkSize)
            {
                firstShrinkable = n;
                break;
            }
        }

        //the structure is already compacted
        if (firstShrinkable == -1)
            return;

        long origSize = elementCount;
        var oldChunks = new ArrayList<>(chunks);
        chunks.clear();

        //move over all of the starting chunks that are already the right size.
        var unchanged = oldChunks.subList(0, firstShrinkable);
        chunks.addAll(unchanged);

        unchanged.clear();

        for (int n = 0; n < oldChunks.size(); ++n)
        {
            var c = oldChunks.get(n);
            oldChunks.set(n, null);
            c.decompress(false);
            var buff = LongBuffer.wrap(c.uncompressed, 0, c.insertPos);
            addAll(buff);
        }
        elementCount = origSize;
    }

    public String toString()
    {
        return "Size = " + elementCount;
    }

    /**
     * Remove all elements from the list.
     */
    public void clear()
    {
        elementCount = 0;
        chunks.clear();
        chunks.add(new Chunk(0));
    }

    /**
     * In order to quickly binary search the list of chunks, each chunk has to know
     * what the index of its first element is. This resets all indexes once
     * there has been an add or remove operation
     * @param chunkListIdxStart The chunk to start renumbering at
     */
    private void updateOffsets(int chunkListIdxStart)
    {
        if (chunkListIdxStart == 0)
        {
            chunks.get(0).offset = 0;
            chunkListIdxStart++;
        }

        for (int n = chunkListIdxStart; n < chunks.size(); ++n)
            chunks.get(n).offset = chunks.get(n-1).nextOffset();
    }

    /**
     * Given a list index, this finds the chunk the index is in.
     * @param idx The list index to search for.
     * @return The required information about the found chunk
     */
    private ChunkInfo getChunk(long idx)
    {
        int bIdx = runBinarySearchIteratively(idx);
        if (bIdx < 0)
            bIdx = -bIdx - 2;
        return new ChunkInfo(chunks.get(bIdx), chunks.get(bIdx).offset, bIdx);
    }

    private int runBinarySearchIteratively(long key) {
        int low= 0;
        int high = chunks.size() - 1;

        while (low <= high) {
            int mid = low  + ((high - low) / 2);
            if (chunks.get(mid).offset < key) {
                low = mid + 1;
            } else if (chunks.get(mid).offset > key) {
                high = mid - 1;
            } else if (chunks.get(mid).offset == key) {
                return mid;
            }
        }
        return -(low + 1);
    }

    /**
     * Maintian the list of decompressed data.
     * @param chunk A chunk that was just decompressed.
     */
    private void addDecompressed(Chunk chunk)
    {
        decompressedChunks.add(chunk);
        while (decompressedChunks.size() > allowedDecompressedCount)
        {
            decompressedChunks.remove(0).compress();
        }
    }

    private class ChunkInfo
    {
        Chunk chunk;
        long indexOffset;
        int listIdx;

        ChunkInfo(Chunk c, long first, int lstPos)
        {
            chunk = c;
            indexOffset = first;
            listIdx = lstPos;
        }

        long correctedIdx(long idx)
        {
            return idx - indexOffset;
        }
    }

    /**
     * This class represents a sub-section of the list
     */
    class Chunk implements Comparable<Long>
    {
        /** The values stored by the chunk. */
        long[] uncompressed;
        /** Where in the uncompressed values we can insert the next element. **/
        int insertPos = 0;
        /** The chunk as compressed binary data. */
        byte[] compressed = null;

        /** The offset in the overall list where this chunk's first element is. */
        long offset;


        Chunk(long offset)
        {
            this(offset, true);
        }

        Chunk(long offset,boolean track)
        {
            uncompressed = new long[chunkSize];
            this.offset = offset;
            if (track)
                addDecompressed(this);
        }

        long nextOffset()
        {
            return offset + insertPos;
        }

        long usedMemory()
        {
            long ret = 0;

            if (uncompressed != null)
                ret = uncompressed.length * BYTE_COUNT;
            if (compressed != null)
                ret += compressed.length;
            return ret;
        }

        boolean append(long v)
        {
            if (insertPos >= chunkSize)
                return false;

            decompress();
            uncompressed[insertPos++] = v;
            return true;
        }

        void append(LongBuffer buff)
        {
            if (insertPos >= chunkSize)
                return;

            decompress();
            int copyLen = uncompressed.length - insertPos;
            copyLen = Math.min(copyLen, buff.limit() - buff.position());
            buff.get(uncompressed, insertPos, copyLen);
            insertPos += copyLen;
        }

        Chunk[] insert(int idx, long v)
        {
            decompress();
            compressed = null;

            //Simple case - there's room, add it
            if (insertPos < uncompressed.length)
            {
                for (int n = uncompressed.length - 2; n >= idx; --n)
                    uncompressed[n + 1] = uncompressed[n];
                uncompressed[idx] = v;
                insertPos++;
                return new Chunk[] {this};
            }

            var postLoad = (insertPos + 1) / (double)chunkSize;

            // We are allowed to grow, so grow and insert
            if (postLoad <= overloadFactor)
            {
                var dest = new long[uncompressed.length + 1];
                System.arraycopy(uncompressed, 0, dest, 0, idx);
                dest[idx] = v;
                System.arraycopy(uncompressed, idx, dest, idx + 1, dest.length - idx - 1);
                uncompressed = dest;
                insertPos++;
                return new Chunk[] {this};
            }

            // The chunk is too big, so create a new chunk and split the data across both
            var ret = new Chunk[] { this, new Chunk(0, false) };
            int splitAt = insertPos / 2;
            System.arraycopy(uncompressed, splitAt, ret[1].uncompressed, 0, insertPos - splitAt);
            ret[1].insertPos = insertPos - splitAt;
            insertPos = splitAt;
            Arrays.fill(uncompressed, insertPos, uncompressed.length, 0);
            if (idx <= insertPos)
                insert(idx, v);
            else
                ret[1].insert(idx - splitAt, v);

            addDecompressed(ret[1]);
            return ret;
        }


        long get(long idx)
        {
            decompress();
            return uncompressed[(int)idx];
        }

        void getAll(LongBuffer buff)
        {
            decompress();
            buff.put(uncompressed, 0, insertPos);
        }

        long set(long idx, long v)
        {
            decompress();
            compressed = null;
            var ret = uncompressed[(int)idx];
            uncompressed[(int)idx] = v;
            return ret;
        }

        long remove(long idx)
        {
            decompress();
            compressed = null;

            var ret = uncompressed[(int)idx];
            System.arraycopy(uncompressed, (int) idx + 1, uncompressed, (int) idx, insertPos - 1 - (int) idx);
            insertPos--;
            return ret;
        }

        void compress()
        {
            if (compressed != null)
            {
                uncompressed = null;
                return;
            }

            var bb = ByteBuffer.allocate(insertPos * (int)BYTE_COUNT);
            bb.order(ByteOrder.nativeOrder());
            bb.asLongBuffer().put(uncompressed, 0, insertPos);
            uncompressed = null;

            var bout = new ByteArrayOutputStream();
            try (var out = new DeflaterOutputStream(bout)) {
                out.write(bb.array());
                out.finish();
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }

            compressed = bout.toByteArray();
        }

        void decompress()
        {
            decompress(true);
        }

        void decompress(boolean track)
        {
            if (uncompressed != null)
                return;

            if (track)
                addDecompressed(this);

            var bin = new ByteArrayInputStream(compressed);
            try (var in = new InflaterInputStream(bin))
            {
                var bb = ByteBuffer.allocate(insertPos * (int)BYTE_COUNT);
                bb.order(ByteOrder.nativeOrder());
                int offset = 0;
                while (bb.limit() != offset)
                {
                    int read = in.read(bb.array(), offset, insertPos * (int)BYTE_COUNT - offset);
                    offset += read;
                }
                uncompressed = new long[insertPos];
                bb.asLongBuffer().get(uncompressed);
            }
            catch (IOException ex)
            {
                ex.printStackTrace();
            }
        }


        @Override
        public int compareTo(Long o) {
            return (int)(offset - o);
        }

        public String toString()
        {
            return String.format("count=%d, is compressed=%b", insertPos, compressed != null);
        }

        IteratorLong floatIterator()
        {
            decompress();

            return new IteratorLong() {
                int idx;

                @Override
                public boolean hasNext() {
                    return idx < insertPos;
                }

                @Override
                public long next() {
                    return uncompressed[idx++];
                }
            };
        }
    }
}
