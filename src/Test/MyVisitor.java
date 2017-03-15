package Test;

import spatialindex.spatialindex.IData;
import spatialindex.spatialindex.INode;
import spatialindex.spatialindex.IVisitor;

/**
 * Created by Administrator on 2017/3/6 0006.
 */
public class MyVisitor implements IVisitor {
    public int m_indexIO = 0;
    public int m_leafIO = 0;

    public void visitNode(final INode n)
    {
        if (n.isLeaf()) m_leafIO++;
        else m_indexIO++;
    }

    public String visitData(final IData d)
    {
        String Keyword = new String(d.getData());
        //System.out.println("结果点的id为：" + d.getIdentifier());
        // the ID of this data entry is an answer to the query. I will just print it to stdout.
        return Keyword;
    }
}
