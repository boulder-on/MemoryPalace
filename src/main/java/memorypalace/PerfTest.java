package memorypalace;

import java.nio.DoubleBuffer;
import java.util.ArrayList;
import java.util.List;

public class PerfTest {

    static final int TEST_SIZE = 10_000_000;
    static DoubleBuffer db = DoubleBuffer.allocate(360);

    public static void main(String[] args) {

        for (int n = 0; n < 360; ++n)
            db.put(n);

        for (int n = 0; n < 10; ++n)
        {
            runCompressor();
            runComparison();
        }

    }

    public static ListOfDouble runCompressor() {
        System.gc();System.gc();System.gc();System.gc();System.gc();
        var lst = new ListOfDouble(1024 * 16, 1.1, 4);
        var names = new ArrayList<String>();
        var times = new ArrayList<Long>();

        long st = System.currentTimeMillis();
        while (lst.size() < TEST_SIZE)
            lst.addAll(db.position(0));
        names.add("Add");times.add((System.currentTimeMillis() - st));

        st = System.currentTimeMillis();
        double s =0;
        for (int n = 0; n < lst.size(); ++n)
            s += lst.get(n);
        names.add("Get");times.add((System.currentTimeMillis() - st));


        st = System.currentTimeMillis();
        long m = lst.stream().filter(d -> d < 30).count();
        names.add("Stream");times.add((System.currentTimeMillis() - st));

        st = System.currentTimeMillis();
        var i = lst.iterator();
        while (i.hasNext())
        {
            double d = i.next();
            if (d > 30)
                m++;
        }
        names.add("Iterator");times.add((System.currentTimeMillis() - st));

        st = System.currentTimeMillis();
        for (int n = 10_050; n < 15_000; ++n)
            lst.remove(10_050);
        names.add("Remove");times.add((System.currentTimeMillis() - st));

        StringBuilder sb = new StringBuilder("Compressed: ");
        for (int n = 0; n < names.size(); ++n)
            sb.append(String.format("%10s", names.get(n))).append("=").append(String.format("%6d", times.get(n)));
        System.out.println(sb);

        return lst;
    }

    public static List<Double> runComparison()
    {
        System.gc();System.gc();System.gc();System.gc();System.gc();
        var names = new ArrayList<String>();
        var times = new ArrayList<Long>();

        var lstDbl = new ArrayList<Double>();
        for (double v : db.array())
            lstDbl.add(v);

        var aList = new ArrayList<Double>();
        long st = System.currentTimeMillis();
        while (aList.size() < TEST_SIZE)
            aList.addAll(lstDbl);
        names.add("Add");times.add((System.currentTimeMillis() - st));

        st = System.currentTimeMillis();
        double s =0;
        for (int n = 0; n < aList.size(); ++n)
            s += aList.get(n);
        names.add("Get");times.add((System.currentTimeMillis() - st));

        st = System.currentTimeMillis();
        long m = aList.stream().filter(d -> d < 30).count();
        names.add("Stream");times.add((System.currentTimeMillis() - st));

        st = System.currentTimeMillis();
        for (double d : aList) {
            if (d > 30)
                m++;
        }
        names.add("Iterator");times.add((System.currentTimeMillis() - st));

        st = System.currentTimeMillis();
        aList.subList(10_050, 15_000).clear();
        names.add("Remove");times.add((System.currentTimeMillis() - st));

        StringBuilder sb = new StringBuilder("List      : ");
        for (int n = 0; n < names.size(); ++n)
            sb.append(String.format("%10s", names.get(n))).append("=").append(String.format("%6d", times.get(n)));
        System.out.println(sb);

        return aList;
    }
}
