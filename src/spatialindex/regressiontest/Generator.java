package spatialindex.regressiontest;
import java.util.*;
import java.io.*;


//-------------------MyRegion----------------------------------------------
class MyRegion
{
	public double m_xmin;
	public double m_ymin;
	public double m_xmax;
	public double m_ymax;

	public MyRegion(double x1, double y1, double x2, double y2)
	{
		m_xmin = (x1 < x2) ? x1 : x2;
		m_ymin = (y1 < y2) ? y1 : y2;
		m_xmax = (x1 > x2) ? x1 : x2;
		m_ymax = (y1 > y2) ? y1 : y2;
	}

	public boolean intersects(MyRegion r)    //閸掋倖鏌囬弰顖氭儊閸滃苯鍑￠惌顧揃R娴溿倕寮�
	{
		if (m_xmin > r.m_xmax || m_xmax < r.m_xmin ||
				m_ymin > r.m_ymax || m_ymax < r.m_ymin) return false;

		return true;
	}

	public double getMinDist(final MyRegion r)//瀵版鍩岄崪灞藉嚒閻檽BR娑斿妫块惃鍕付鐏忓繗绐涚粋锟�
	{
		double ret = 0.0;

		if (r.m_xmin < m_xmin)
			ret += Math.pow(m_xmin - r.m_xmin, 2.0);
		else if (r.m_xmin > m_xmax)
				ret += Math.pow(r.m_xmin - m_xmax, 2.0);

		if (r.m_ymin < m_ymin)
				ret += Math.pow(m_ymin - r.m_ymin, 2.0);
		else if (r.m_ymin > m_ymax)
				ret += Math.pow(r.m_ymin - m_ymax, 2.0);

		return ret;
	}
	//pow閸戣姤鏆熼敍姘崇箲閸ョ偟顑囨稉锟芥稉顏勫棘閺佹壆娈戠粭顑跨癌娑擃亜寮弫鎵畱濞嗏�冲暎閸婏拷
}
//------------------------------------------------------------------------

//--------------------Generator--------------------------------------------
public class Generator
{
	public static void main(String[] args)
	{
		if (args.length != 1)
		{
			System.err.println("Usage: Generator number_of_data.");
			System.exit(-1);
		}

		Random rand = new Random();

		int numberOfObjects = new Integer(args[0]).intValue();
		HashMap data = new HashMap(numberOfObjects);

		//娴犮儰绗呭顏嗗箚閻€劋绨崷銊ョ潌楠炴洝绶崙娲閺堣櫣鏁撻幋鎰畱MBR閿涘苯鑻熼悽鐔稿灇data
		for (int i = 0; i < numberOfObjects; i++)
		{
			MyRegion r = new MyRegion(rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), rand.nextDouble());
			                           //xmin,ymin,xmax,ymax
			data.put(new Integer(i), r);

			System.out.println("1 " + i + " " + r.m_xmin + " " + r.m_ymin + " " + r.m_xmax + " " + r.m_ymax);
			//1:insert
		}

		//------------------濡拷濞达拷---------------------------
		int A = (int) (Math.floor((double) numberOfObjects * 0.1)); //鏉╂柨娲栭張锟芥径褏娈戦敍鍫熸付閹恒儴绻庡锝嗘￥缁屽嘲銇囬敍濉猳uble閸婄》绱濈拠銉ワ拷鐓庣毈娴滃海鐡戞禍搴″棘閺佸府绱濋獮鍓佺搼娴滃孩鐓囨稉顏呮殻閺侊拷

		for (int T = 100; T > 0; T--)
		{
			System.err.println(T);
			HashSet examined = new HashSet();

			for (int a = 0; a < A; a++)
			{
				// find an id that is not yet examined.
				Integer id = new Integer((int) ((double) (numberOfObjects - 1) * rand.nextDouble()));
				boolean b = examined.contains(id);

				while (b)
				{
					id = new Integer((int) ((double) (numberOfObjects - 1) * rand.nextDouble()));
					b = examined.contains(id);
				}
				//閹垫儳鍩屽▽鈩冩箒鐞氼偅顥呭ù瀣箖閻ㄥ埇d娑斿鎮楅敍灞界殺id閸旂姴鍙唀xamined
				examined.add(id);
				MyRegion r = (MyRegion) data.get(id);
                //閸︺劌鐫嗛獮鏇＄翻閸戝搫鍨归梽銈囨畱region
				System.out.println("0 " + id + " " + r.m_xmin + " " + r.m_ymin + " " + r.m_xmax + " " + r.m_ymax);

				r = new MyRegion(rand.nextDouble(), rand.nextDouble(), rand.nextDouble(), rand.nextDouble());
				data.put(id, r);
				//閸︺劌鐫嗛獮鏇＄翻閸戠儤鏌婇崝鐘插弳閻ㄥ嫮娈憆egion
				System.out.println("1 " + id + " " + r.m_xmin + " " + r.m_ymin + " " + r.m_xmax + " " + r.m_ymax);
			}

			double stx = rand.nextDouble();
			double sty = rand.nextDouble();
			System.out.println("2 9999999 " + stx + " " + sty + " " + (stx + 0.01) + " " + (sty + 0.01));
		}
	}
}
//-----------------------------------------------------------------------------------------------------
