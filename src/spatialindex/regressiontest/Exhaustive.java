package spatialindex.regressiontest;
import java.util.*;
import java.io.*;

class NNEntry
{
	public int m_id;    //MBR閻ㄥ埇d
	public double m_dist;   //distance

	NNEntry(int id, double dist) { m_id = id; m_dist = dist; }
}

public class Exhaustive
{
	public static void main(String[] args)
	{
		if (args.length != 2)
		{
			System.err.println("Usage: Exhaustive data_file query_type [intersection | 10NN].");
			System.exit(-1);
		}
		//args[0]:閺傚洣娆㈤崥锟�    args[1]:缁便垹绱╃粔宥囪
		

		LineNumberReader lr = null;

		try
		{
			lr = new LineNumberReader(new FileReader(args[0]));  //閺傚洣娆㈤崥宥忕礉閺傚洣娆㈠ù渚婄礉缂傛挸鍟垮ù锟�
		}
		catch (FileNotFoundException e)
		{
			System.err.println("Cannot open data file" + args[0] + ".");
			System.exit(-1);
		}
		//鐎规矮绠熼弫鐗堝祦閺傚洣娆㈡潏鎾冲弳缂傛挸鍟垮ù锟�
		
		
		HashMap data = new HashMap();
		int id, op;   //id閿涙碍鐖ｇ拠鍡礉閸烆垯绔撮惃鍕剁幢 op閿涙碍鎼锋担婊堬拷澶嬪
		float x1, x2, y1, y2;  //region
		
		
		String line = null;
		try
		{
			line = lr.readLine();  //鐠囪褰囬弫鐗堝祦閺傚洣娆㈡稉顓犳畱娑擄拷鐞涳拷
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}

        //------------------------------------------------------------------------------------------
		while (line != null)
		{
			StringTokenizer st = new StringTokenizer(line);
			op = new Integer(st.nextToken()).intValue();
			id = new Integer(st.nextToken()).intValue();

			if (op == 0)
			{
				//delete
			}
			else if (op == 1)
			{
				//insert
				x1 = new Float(st.nextToken()).floatValue();
				y1 = new Float(st.nextToken()).floatValue();
				x2 = new Float(st.nextToken()).floatValue();
				y2 = new Float(st.nextToken()).floatValue();

				data.put(new Integer(id), new MyRegion(x1, y1, x2, y2));
				//id:MBR閺佹澘鐡ч弽鍥唶      data閿涙ashMap閿涘苯缂撶粩濯攄閸掔檿BR閻ㄥ嫭妲х亸锟�
			}
			else if (op == 2)
			{
				//query
				x1 = new Float(st.nextToken()).floatValue();
				y1 = new Float(st.nextToken()).floatValue();
				x2 = new Float(st.nextToken()).floatValue();
				y2 = new Float(st.nextToken()).floatValue();

				if (args[1].equals("intersection"))   //閳ユ笅ntersection閳ユ繄鍌ㄥ鏇礉濡拷濞村妲搁崥锔芥箒娴溿倕寮�
				{
					MyRegion query = new MyRegion(x1, y1, x2, y2);
					Iterator it = data.entrySet().iterator();
					while (it.hasNext())
					{
						Map.Entry e = (Map.Entry) it.next();
						MyRegion r = (MyRegion) e.getValue();
						Integer i = (Integer) e.getKey();
						if (query.intersects(r))
						{
							System.out.println(i.intValue());
						}
					}
				}
				else if (args[1].equals("10NN"))   //閳ワ拷10NN閳ユ繄鍌ㄥ鏇礉瀵版鍩岀粋绫穟ery閺堬拷鏉╂垹娈慚BR
				{
					MyRegion query = new MyRegion(x1, y1, x1, y1);

					// there is no priority queue implementation in Java. Even TreeSet or TreeMap require unique keys,
					// and since I am sorting according to minDist, I need to be able to keep duplicate values.
					// The problem with Arrays.sort is nlogn time, when the priority queue would be logn.
					
					NNEntry[] queue = new NNEntry[data.size()];//鐎规矮绠熺�电钖勯弫鎵矋

					int cIndex = 0;
					Iterator it = data.entrySet().iterator();  //闁秴宸籨ata
					while (it.hasNext())
					{
						Map.Entry e = (Map.Entry) it.next();
						MyRegion r = (MyRegion) e.getValue();
						Integer i = (Integer) e.getKey();
						
						queue[cIndex++] = new NNEntry(i.intValue(), r.getMinDist(query));
					}
                    //鐠嬪啰鏁rrays缁鑵戦惃鍕饯閹焦鏌熷▔鏇炵殺Queue閹烘帒绨�
					Arrays.sort(queue,
											new Comparator()
											{
												public int compare(Object o1, Object o2)
												{
													NNEntry n1 = (NNEntry) o1;
													NNEntry n2 = (NNEntry) o2;

													if (n1.m_dist < n2.m_dist) return -1;
													if (n1.m_dist > n2.m_dist) return 1;
													return 0;
												}
											});

					int count = 0;
					double knearest = 0.0;

					for (cIndex = 0; cIndex < queue.length; cIndex++)
					{
						if (count >= 10 && queue[cIndex].m_dist > knearest) break;

						System.out.println(queue[cIndex].m_id);
						count++;
						knearest = queue[cIndex].m_dist;
					}
				}
			}//op=2

			try
			{
				line = lr.readLine();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		} // while
        //--------------------------------------------------------------------------------------------------------
		
	} // main
}
