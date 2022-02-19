package memorypalace;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class TestListOfFloat {
    public ListOfFloat getArr()
    {
        return new ListOfFloat(1000);
    }

    @Test
    public void testAdd()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();

        int count = 10000;
        for (int n = 0; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        Assert.assertEquals(aList.size(), lst.size());

        for (int n = 0; n < count; ++n)
            Assert.assertEquals(aList.get(n), lst.get(n), 1e-10);

        Assert.assertTrue(lst.usedMemory() < lst.uncompressedSize());

        var iter = lst.iterator();
        var aiter = aList.iterator();
        while (iter.hasNext())
            Assert.assertEquals(aiter.next(), iter.next(), 1e-10);

        lst.clear();
        aList.clear();
        Assert.assertEquals(aList.isEmpty(), lst.isEmpty());
    }

    @Test
    public void testAddAll()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();
        int count = 10000;
        for (int n = 0; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        Assert.assertFalse(lst.isEmpty());
        lst.addAll(new float[] {0, 1, 2, 3, 4, 5, 6,7,8,9});
        Assert.assertTrue(aList.addAll(Arrays.asList(0.0f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 6.0f,7.0f,8.0f,9.0f)));

        Assert.assertEquals(aList.size(), lst.size());
        Assert.assertEquals(aList.get(count + 1), lst.get(count + 1), 1e-10);
        Assert.assertEquals(aList.get(aList.size() - 1), lst.get(lst.size() -1), 1e-10);

        compareLists(aList, lst);

    }

    @Test
    public void testInsert()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();
        int count = 5000;
        for (int n = 0; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        for (int n = count; n < count + 100; ++n)
        {
            lst.add(n, n);
            aList.add(n, (float)n);
        }

        for (int n = count + 1; n < count + 101; ++n)
        {
            lst.add(count, n);
            aList.add(count, (float)n);
        }

        Assert.assertEquals(aList.size(), lst.size());

        lst.add(0, -1);
        aList.add(0, -1.0f);
        lst.add(1, -2);
        aList.add(1, -2.0f);
        Assert.assertEquals(aList.get(0), lst.get(0), 1e-10);
        Assert.assertEquals(aList.get(1), lst.get(1), 1e-10);
        Assert.assertEquals(aList.get(2), lst.get(2), 1e-10);

        lst = new ListOfFloat(10, 1.5, 2);
        lst.addAll(new float[]{0, 1, 2, 3, 4, 5, 6, 7, 8});
        lst.add(5, -1);
        aList = new ArrayList<>();
        aList.addAll(Arrays.asList(0.0f, 1.0f, 2.0f, 3.0f, 4.0f ,5.0f ,6.0f ,7.0f, 8.0f));
        aList.add(5, -1.0f);

        Assert.assertEquals(aList.get(5), lst.get(5), 1e-10);
        compareLists(aList, lst);

        for (int n = 2; n <= 7; ++n)
        {
            lst.add(5, -n);
            aList.add(5, (float)-n);
        }
        compareLists(aList, lst);
        var arr = lst.toArray();
        var aArr = aList.stream().mapToDouble(d -> d).toArray();
        var aArr2 = new float[aArr.length];
        for (int n = 0; n < aArr.length; ++n)
            aArr2[n] = (float)aArr[n];

        Assert.assertArrayEquals(aArr2, arr,1e-10f);

        lst.clear();
        aList.clear();
        for (int n = 0; n < 30; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }
        for (int n = 1; n < 11; ++n) {
            lst.add(19, -n);
            aList.add(19, (float)-n);
        }

        double[] arr2 = lst.stream().toArray();
        aArr = aList.stream().mapToDouble(d -> d).toArray();
        Assert.assertArrayEquals(aArr, arr2, 1e-10);

        compareLists(aList, lst);
    }

    @Test
    public void testRemove()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();

        int count = 10000;
        for (int n = 0; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        Assert.assertEquals(aList.remove(0), lst.remove(0), 1e-10);
        Assert.assertEquals(count - 1, lst.size());
        Assert.assertEquals(aList.get(0), lst.get(0), 1e-10);
        compareLists(aList, lst);
    }

    @Test
    public void testSet()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();

        int count = 5000;
        for (int n = 1; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        for (int n = count; n < count + 100; ++n)
        {
            Assert.assertEquals(aList.set(count - 10, (float)n), lst.set(count - 10, n), 1e-10);
        }

        Assert.assertEquals(aList.size(), lst.size());

        lst.set(0, -1);
        lst.set(1, -2);
        aList.set(0, -1.0f);
        aList.set(1, -2.0f);

        Assert.assertEquals(aList.get(0), lst.get(0), 1e-10);
        Assert.assertEquals(aList.get(1), lst.get(1), 1e-10);
        Assert.assertEquals(aList.get(2), lst.get(2), 1e-10);

        compareLists(aList, lst);
    }

    @Test
    public void testTrimToSize()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();

        int count = 5000;
        for (int n = 1; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        Assert.assertEquals(aList.size(), lst.size());
        long size = lst.usedMemory();
        lst.trimToSize();
        Assert.assertEquals(size, lst.usedMemory());
        Assert.assertEquals(aList.size(), lst.size());

        for (int n = 1; n < 700; ++n) {
            lst.add(2200 + n, -n);
            aList.add(2200 + n, (float)-n);
            Assert.assertEquals(aList.size(), lst.size());
        }

        Assert.assertEquals(aList.size(), lst.size());
        size = lst.usedMemory();
        lst.trimToSize();
        Assert.assertTrue(size > lst.usedMemory());
        Assert.assertEquals(aList.size(), lst.size());

        compareLists(aList, lst);
    }

    @Test
    public void testNoOverload()
    {
        var lst = new ListOfFloat(50, 1, 2);
        var aList = new ArrayList<Float>();

        int count = 5000;
        for (int n = 1; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        Assert.assertEquals(aList.size(), lst.size());
        long size = lst.usedMemory();
        lst.trimToSize();
        Assert.assertEquals(size, lst.usedMemory());
        Assert.assertEquals(aList.size(), lst.size());

        for (int n = 1; n < 700; ++n) {
            lst.add(2200 + n, -n);
            aList.add(2200 + n, (float)-n);
            Assert.assertEquals(aList.size(), lst.size());
        }

        Assert.assertEquals(aList.size(), lst.size());
        size = lst.usedMemory();
        lst.trimToSize();
        Assert.assertTrue(size > lst.usedMemory());
        compareLists(aList, lst);
    }

    void compareLists(List<Float> aList, ListOfFloat lst)
    {
        Assert.assertEquals(aList.size(), lst.size());

        for (int n = 0; n < lst.size(); ++n)
            Assert.assertEquals(aList.get(n), lst.get(n), 1e-10);
    }

    @Test
    public void testExceptions()
    {
        Assert.assertThrows(IllegalArgumentException.class, () -> new ListOfFloat(-1));
        Assert.assertThrows(IllegalArgumentException.class, () -> new ListOfFloat(1));
        Assert.assertThrows(IllegalArgumentException.class, () -> new ListOfFloat(100, .99, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> new ListOfFloat(100, 2.01, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> new ListOfFloat(100, 1.5, 0));
        Assert.assertThrows(IllegalArgumentException.class, () -> new ListOfFloat(100).add(10, 1));
        Assert.assertThrows(IllegalArgumentException.class, () -> new ListOfFloat(100).remove(10));
    }

    @Test
    public void testForEach()
    {
        var lst = getArr();
        int count = 5000;
        for (int n = 1; n < count; ++n)
            lst.add(n);
        var aList = new ArrayList<Float>();
        lst.forEach(aList::add);
        compareLists(aList, lst);
    }

    @Test
    public void testIterator()
    {
        var lst = getArr();
        int count = 5000;
        for (int n = 1; n < count; ++n)
            lst.add(n);

        var aList = new ArrayList<Float>();

        var iter = lst.listIterator(0);
        int idx = 1;

        while (iter.hasNext())
        {
            Assert.assertEquals(idx++, iter.nextIndex());
            aList.add(iter.next());
        }
        compareLists(aList, lst);
    }

    @Test
    public void testStream()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();

        Random r = new Random();
        for (int n = 0; n < 200; ++n)
        {
            float v = r.nextFloat();
            lst.add(v);
            aList.add(v);
        }

        compareLists(aList, lst);
        var arr = lst.stream().toArray();

        double sum = lst.stream().sum();
        double sum2 = aList.stream().mapToDouble(Float::doubleValue).sum();
        Assert.assertEquals(sum2, sum, 1e-10);
    }

    @Test
    public void testFloatIterator()
    {
        var lst = getArr();
        var aList = new ArrayList<Float>();

        int count = 10000;
        for (int n = 0; n < count; ++n)
        {
            lst.add(n);
            aList.add((float)n);
        }

        var dIter = lst.floatIterator();
        var iter = aList.iterator();
        int nextCount = 0;

        while (dIter.hasNext())
        {
            Assert.assertEquals(iter.next(), dIter.next(), 1e-10);
            nextCount++;
        }

        Assert.assertEquals(count, nextCount);
    }
}

