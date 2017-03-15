// Spatial Index Library
//
// Copyright (C) 2002  Navel Ltd.
//
// This library is free software; you can redistribute it and/or
// modify it under the terms of the GNU Lesser General Public
// License as published by the Free Software Foundation; either
// version 2.1 of the License, or (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License aint with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
//
// Contact information:
//  Mailing address:
//    Marios Hadjieleftheriou
//    University of California, Riverside
//    Department of Computer Science
//    Surge Building, Room 310
//    Riverside, CA 92521
//
//  Email:
//    marioh@cs.ucr.edu

package spatialindex.rtree;

import java.util.*;
import java.util.function.Consumer;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import Test.IRPoint;
import Test.MyVisitor;
import Test.newTest;
import spatialindex.regressiontest.Exhaustive;
import spatialindex.spatialindex.*;
import spatialindex.storagemanager.*;

public class RTree implements ISpatialIndex
{
	RWLock m_rwLock;

	IStorageManager m_pStorageManager;   //存储器管理

	int m_rootID;
	int m_headerID;

	int m_treeVariant;

	double m_fillFactor;

	int m_indexCapacity;

	int m_leafCapacity;

	int m_nearMinimumOverlapFactor;   //factor：因素     overlap：重叠
		// The R*-Tree 'p' constant（常量）, for calculating nearly minimum overlap cost.
		// [Beckmann, Kriegel, Schneider, Seeger 'The R*-tree: An efficient and Robust Access Method
		// for Points and Rectangles, Section 4.1]

	double m_splitDistributionFactor;   //split：分裂，分解
		// The R*-Tree 'm' constant, for calculating spliting distributions.
		// [Beckmann, Kriegel, Schneider, Seeger 'The R*-tree: An efficient and Robust Access Method 
		// for Points and Rectangles, Section 4.2]

	double m_reinsertFactor;
		// The R*-Tree 'p' constant, for removing entries at reinserts.
		// [Beckmann, Kriegel, Schneider, Seeger 'The R*-tree: An efficient and Robust Access Method
		//  for Points and Rectangles, Section 4.3]

	int m_dimension;

	Region m_infiniteRegion;   //infinite：[数]无穷大

	Statistics m_stats;
	
	Node n;
	
	ArrayList m_writeNodeCommands = new ArrayList();
	ArrayList m_readNodeCommands = new ArrayList();
	ArrayList m_deleteNodeCommands = new ArrayList();

	public RTree(PropertySet ps, IStorageManager sm) {
		m_rwLock = new RWLock();
		m_pStorageManager = sm;
		m_rootID = IStorageManager.NewPage;//IStorageManager的静态属性，默认为-1
		m_headerID = IStorageManager.NewPage;
		m_treeVariant = SpatialIndex.RtreeVariantRstar;
		m_fillFactor = 0.7f;
		m_indexCapacity = 2;
		m_leafCapacity = 2;
		m_nearMinimumOverlapFactor = 32;
		m_splitDistributionFactor = 0.4f;
		m_reinsertFactor = 0.3f;
		m_dimension = 2;

		m_infiniteRegion = new Region();
		m_stats = new Statistics();

		Object var = ps.getProperty("IndexIdentifier");
		if (var != null)
		{
			if (! (var instanceof Integer)) throw new IllegalArgumentException("Property IndexIdentifier must an Integer");
			m_headerID = ((Integer) var).intValue();
			try
			{
				initOld(ps);
			}
			catch (IOException e)
			{
				System.err.println(e);
				throw new IllegalStateException("initOld failed with IOException");
			}
		}
		else
		{
			try
			{
				initNew(ps);
			}
			catch (IOException e)
			{
				System.err.println(e);
				throw new IllegalStateException("initNew failed with IOException");
			}
			Integer i = new Integer(m_headerID);
			ps.setProperty("IndexIdentifier", i);
		}
	}

	HashMap<Integer,Integer> findfather = new HashMap<>();
	List<Integer> allleafnodeid = new ArrayList<>();

	public void BuiltHashMapAndSet(HashMap<Integer,Integer> findfather, List<Integer> allleafnodeid){
		Queue<Node> lis = new LinkedList<>();

		Node n1 = readNode(m_rootID);
		lis.add(n1);
		if (n1 == null){
			System.out.println("root is null");
		}

		while (!lis.isEmpty()){
			//弹出队列中的第一个元素
			Node temp = lis.poll();

			//判断这个元素是不是叶子节点，如果是则加入set，如果不是则将子节点加入队列，并想hashmap中添加值
			if (temp.getLevel() == 0) {
				allleafnodeid.add(temp.getIdentifier());//如果是叶节点，就将所有的叶节点加入到这个set中
			}else {
				for (int i=0; i<temp.m_children; i++){
					Node n2 = readNode(temp.m_pIdentifier[i]);
					lis.add(n2);
					findfather.put(temp.m_pIdentifier[i], temp.getIdentifier());
				}
			}
		}
	}

	public void BuildInvertedList() throws IOException {
//		n = readNode(m_rootID);
		BuiltHashMapAndSet(findfather, allleafnodeid);
		BuildInvertedFile(findfather, allleafnodeid);
	}

	public void BuildInvertedFile(HashMap<Integer,Integer> findfather, List<Integer> allleafnodeid) throws IOException {
		int[] isVisit = new int[newTest.sumPoints];

		Queue<Integer> fathernode = new LinkedList<>();
		//读取叶节点，对叶节点建立倒排表
//		System.out.println("孩子节点的数量"+allleafnodeid.size());
		for (int i=0; i<allleafnodeid.size(); i++) {

//			System.out.println("孩子"+i);
			Node e = readNode(allleafnodeid.get(i));

			int fatherid = findfather.getOrDefault(allleafnodeid.get(i),-1);
			if ((fatherid != -1) && (isVisit[fatherid] != 1)) {
				fathernode.add(fatherid);
				isVisit[fatherid] = 1;
			}

			for (int j=0; j<e.m_children; j++){
				int id = e.m_pIdentifier[j];
//				System.out.println("id为"+id);
				ArrayList<String> bucket = newTest.map.get(id).bucketnumber;
				if (bucket.size() <1 ){
					System.out.println("bucket is null");
				}
				for(int k=0; k<bucket.size(); k++)
				{
					if (e.m_invertedList == null){
						System.out.println("倒排表为空");
					}
					if(!e.m_invertedList.containsKey(bucket.get(k))){
						ArrayList temp = new ArrayList();
						temp.add(id);
						e.m_invertedList.put(bucket.get(k), temp);
					}else{//若是已经包含了此关键词，则加入到已存在的数组中
						ArrayList temp = e.m_invertedList.get(bucket.get(k));
						temp.add(id);
						e.m_invertedList.put(bucket.get(k), temp);
					}
				}
			}

			//将当前节点的已经建好的倒排表写入相应的文件
			writeInvertList(e);
		}

		//建立父节点倒排表
		while (!fathernode.isEmpty()){
//			System.out.println("父节点个数"+fathernode.size());
			int id = fathernode.poll();
			Node e = readNode(id);

//			System.out.println("父节点的倒排表："+e.m_invertedList.size());

			int fatherid = findfather.getOrDefault(id,-1);
			if ((fatherid != -1) && (isVisit[fatherid] != 1)) {
				fathernode.add(fatherid);
				isVisit[fatherid] = 1;
			}

//			Node e2 = readNode(fatherid);
//			System.out.println("建立倒排文件之前的普通节点："+e2.m_invertedList.size());
			for (int i=0; i<e.m_children; i++){
				int childid = e.m_pIdentifier[i];

				Node e2 = readNode(childid);

				String keytemp;
				Set<String> keyset = e2.m_invertedList.keySet();
				Iterator<String> it = keyset.iterator();
				while (it.hasNext()) {
					keytemp = it.next();
					if (!e.m_invertedList.containsKey(keytemp)) {
						ArrayList valuetemp = new ArrayList();
						valuetemp.add(childid);
						e.m_invertedList.put(keytemp, valuetemp);
					} else {
						ArrayList valuetemp = e.m_invertedList.get(keytemp);
						valuetemp.add(childid);
						e.m_invertedList.put(keytemp, valuetemp);
					}
				}
			}
			writeInvertList(e);
//			System.out.println("建立倒排文件之后的普通节点："+e.m_invertedList.size());
//			System.out.println();
		}
	}

	private void writeInvertList(Node n) throws IOException {
		String filename = "D:\\LSHdata\\InvertedList\\InvertedList" + n.getIdentifier() + ".txt";
		FileWriter fw = new FileWriter(filename);

		fw.write(n.m_invertedList.toString());

		fw.close();
	}


	//
	// ISpatialIndex interface
	//

	public void insertData(final byte[] data, final IShape shape, int id) {
		if (shape.getDimension() != m_dimension) throw new IllegalArgumentException("insertData: IShape dimensionality is incorrect.");

		m_rwLock.write_lock();

		try
		{
			Region mbr = shape.getMBR();

			byte[] buffer = null;

			if (data != null && data.length > 0)
			{
				buffer = new byte[data.length];
				System.arraycopy(data, 0, buffer, 0, data.length);
			}

			insertData_impl(buffer, mbr, id);
				// the buffer is stored in the tree. Do not delete here.		
		}
		finally
		{
			m_rwLock.write_unlock();
		}
	}

	public boolean deleteData(final IShape shape, int id) {
		if (shape.getDimension() != m_dimension) throw new IllegalArgumentException("deleteData: IShape dimensionality is incorrect.");

		m_rwLock.write_lock();

		try
		{
			Region mbr = shape.getMBR();
			return deleteData_impl(mbr, id);
		}
		finally
		{
			m_rwLock.write_unlock();
		}
	}

	public void containmentQuery(final IShape query, final IVisitor v) {
		if (query.getDimension() != m_dimension) throw new IllegalArgumentException("containmentQuery: IShape dimensionality is incorrect.");
		rangeQuery(SpatialIndex.ContainmentQuery, query, v);
	}

	public void intersectionQuery(final IShape query, final IVisitor v) {
		if (query.getDimension() != m_dimension) throw new IllegalArgumentException("intersectionQuery: IShape dimensionality is incorrect.");
		rangeQuery(SpatialIndex.IntersectionQuery, query, v);
	}

	public void pointLocationQuery(final IShape query, final IVisitor v) {
		if (query.getDimension() != m_dimension) throw new IllegalArgumentException("pointLocationQuery: IShape dimensionality is incorrect.");
		
		Region r = null;
		if (query instanceof Point)
		{
			r = new Region((Point) query, (Point) query);
		}
		else if (query instanceof Region)
		{
			r = (Region) query;
		}
		else
		{
			throw new IllegalArgumentException("pointLocationQuery: IShape can be Point or Region only.");
		}

		rangeQuery(SpatialIndex.IntersectionQuery, r, v);
	}

	public IRPoint NNquery(final IShape query, String keyword){
//		System.out.println("query keyword:"+keyword);
		int[] isVisit = new int[newTest.sumPoints];

		IRPoint po = new IRPoint();

		Queue<Integer> idtemp = new LinkedList<>();
		List<Integer> resulttemp = new ArrayList<>();
		Node e = readNode(m_rootID);
		if (e.m_invertedList.containsKey(keyword)){
			idtemp.addAll(e.m_invertedList.get(keyword));
//			System.out.println("包含了关键字的子节点个数："+idtemp.size());
		}else
			return null;

		if (idtemp.isEmpty()){
			System.err.println("error with idtemp!!!!!");
			return null;
		}

		while (!idtemp.isEmpty()){
//			System.out.println("idtemp is not empty");
			Node e2 = readNode(idtemp.poll());

			if (e2.getLevel() == 0){//如果是叶节点
				//leaf
				if (e2.m_invertedList.containsKey(keyword)){
					for (int i=0; i<e2.m_invertedList.get(keyword).size();i++){
						int temp = e2.m_invertedList.get(keyword).get(i);
						if (isVisit[temp] != 1){
//							System.out.println("对象id"+temp);
							resulttemp.add(temp);
							isVisit[temp] = 1;
						}
					}
				}
			}else {//如果是普通节点
				//node
				if (e2.m_invertedList.containsKey(keyword)){
					idtemp.addAll(e2.m_invertedList.get(keyword));
				}
			}
		}

		if (resulttemp.size() < 1){
			System.err.println("error!!!!!");
			return null;
		}

//		System.out.println("包含这个关键字的对象的个数："+resulttemp.size());
		double min_distan = Double.MAX_VALUE;
		for (int i1=0; i1<resulttemp.size(); i1++){
//			System.out.println("in finding minimum distance");
			double curdistan;
			//取出相应id对应的IRPoint（的坐标）,根据之后的公式计算距离，在过程中保持最小距离对应的object
			IRPoint t = newTest.map.get(resulttemp.get(i1));
			curdistan = Calcu_dist(t,query);
			if (curdistan<min_distan){
				min_distan = curdistan;
				po = t;
//				System.out.println(resulttemp.get(i1)+","+min_distan);
			}
		}

		if (po == null){
			System.out.println("NNquery po is null");
		}
		return po;
	}

	public IRPoint nearestNeighborQuery(int k, final IShape query, String keyword, final IVisitor v)
	{
		IRPoint po;
		if (query.getDimension() != m_dimension) throw new IllegalArgumentException("nearestNeighborQuery: IShape dimensionality is incorrect.");
		NNComparator nnc = new NNComparator();
		po = nearestNeighborQuery(k, query, keyword, v, nnc);
		return po;
	}

	public IRPoint nearestNeighborQuery(int k, final IShape query, String keyword, final IVisitor v, final INearestNeighborComparator nnc)
	{
		/*根据已经创建好的一个LIR-tree,从根节点开始找起，判断根节点当中是否包含keyword,
		如果包含，读取相应的一组id,判断接下来的节点是Node还是leaf
			如果是Node，并且Node中包含keyword，那么读取相应的id，判断接下来的节点是Node还是leaf
			如果是leaf，读取object的id，并计算距离
		 */

		IRPoint po = new IRPoint();

		try
		{
			ArrayList queue = new ArrayList();
			ArrayList Result_queue = new ArrayList();

			Node n = readNode(m_rootID);
			queue.add(new NNEntry(n, 0.0));//加入根节点，距离为0

			int count = 0;
			double knearest = 0.0;

			while (queue.size() != 0)
			{
				NNEntry first = (NNEntry) queue.remove(0);

				if (first.m_pEntry instanceof Node)//如果first.m_pEntry是Node的一个实例
				{
					System.out.println("is a Node");
					n = (Node) first.m_pEntry;
					v.visitNode((INode) n);//访问节点n

					if (n.m_invertedList.containsKey(keyword)){
						for (int cChild = 0; cChild < n.m_children; cChild++) {
							IEntry e;

							if (n.m_level == 0) {
								e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
								String word = new String(n.m_pData[cChild]);

								if(word.equals(keyword)){
									NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(query, e));

									Result_queue.add(nnc.getMinimumDistance(query, e));//将关键字相同的点之间的距离存到数组当中

									int loc = Collections.binarySearch(queue, e2, new NNEntryComparator());
									if (loc >= 0) queue.add(loc, e2);//增加条件，使得查询到的符合条件的点的关键字与查询的关键字相同
									else queue.add((-loc - 1), e2);
								}
							}
							else {
								e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点


								NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(query, e));

								int loc = Collections.binarySearch(queue, e2, new NNEntryComparator());
								if (loc >= 0) queue.add(loc, e2);
								else queue.add((-loc - 1), e2);
							}
						}
					}
				}
				else
				{
					System.out.println("is an object");
					// report all nearest neighbors with equal furthest distances.
					// (neighbors can be more than k, if many happen to have the same
					//  furthest distance).
					if (count >= k && first.m_minDist > knearest) break;

					v.visitData((IData) first.m_pEntry);

					IRPoint temp = new IRPoint();

					if (!temp.bucketnumber.contains(keyword)) break;
					temp.m_pointID = first.m_pEntry.getIdentifier();

					po = temp;
					m_stats.m_queryResults++;
					count++;
					knearest = first.m_minDist;
				}
			}

			Collections.sort(Result_queue);
//			System.out.println("Key Distances:" + Result_queue);
		}
		finally
		{
			m_rwLock.read_unlock();
		}
		System.out.println("id"+ po.m_pointID);
		return po;
	}
	
	//-----------------------------------------------find top-k----------------------------------------------------------------------------
	
	public List<IRPoint> TOPK(int k, final IShape query, final IVisitor v) {
		List<IRPoint> po = new ArrayList<IRPoint>();
		if (query.getDimension() != m_dimension) throw new IllegalArgumentException("TOPK: IShape dimensionality is incorrect.");
		NNComparator nnc = new NNComparator();
		po = TOPK(k, query, v, nnc);
		return po;
	}

	public List<IRPoint> TOPK(int k, final IShape query, final IVisitor v, final INearestNeighborComparator nnc) {
		List<IRPoint> po = new ArrayList<IRPoint>();

		try
		{
			ArrayList queue = new ArrayList();
			ArrayList Result_queue = new ArrayList();

			Node n = readNode(m_rootID);
			queue.add(new NNEntry(n, 0.0));//加入根节点，距离为0

			int count = 0;
			double knearest = 0.0;

			while (queue.size() != 0)
			{
				NNEntry first = (NNEntry) queue.remove(0);

				if (first.m_pEntry instanceof Node)//如果first.m_pEntry是Node的一个实例
				{
					n = (Node) first.m_pEntry;
					v.visitNode((INode) n);//访问节点n

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						IEntry e;

						if (n.m_level == 0)
						{
							e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
							//如果此节点是叶子节点，则依次读出叶子节点当中的对象的值
							String word = new String(n.m_pData[cChild]);//如果此节点是叶节点，则找出此实例当中的关键字						
							
							NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(query, e));
							//如果e在这里代表着对象，则表示查询点与此对象之间的最小距离；如果e在这里代表着节点，则表示查询点与此节点的MBR之间的距离
							
							// Why don't I use a TreeSet here? See comment above...
							Result_queue.add(nnc.getMinimumDistance(query, e));//将关键字相同的点之间的距离存到数组当中
							
							int loc = Collections.binarySearch(queue, e2, new NNEntryComparator());
							if (loc >= 0) queue.add(loc, e2);//增加条件，使得查询到的符合条件的点的关键字与查询的关键字相同
							else queue.add((-loc - 1), e2);									
						}
						else
						{
							e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点
							
							NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(query, e));
							
							int loc = Collections.binarySearch(queue, e2, new NNEntryComparator());
							if (loc >= 0) queue.add(loc, e2);
							else queue.add((-loc - 1), e2);
						}
					}
				}
				else
				{
					// report all nearest neighbors with equal furthest distances.
					// (neighbors can be more than k, if many happen to have the same
					//  furthest distance).
					if (count >= k && first.m_minDist > knearest) break;

					v.visitData((IData) first.m_pEntry);

					IRPoint temp = new IRPoint();
					temp.m_pointID = first.m_pEntry.getIdentifier();
					//temp.Keyword = keyword;
//					temp.distance = first.m_minDist;

					po.add(temp);
					m_stats.m_queryResults++;
					count++;
					knearest = first.m_minDist;
				}
			}
			
			Collections.sort(Result_queue);
//			System.out.println("Key Distances:" + Result_queue);
		}
		finally
		{
			m_rwLock.read_unlock();
		}
		return po;
	}

	//========================================================================================================
//	public double getCost(List<IRPoint> po, IRPoint query){
//		double a=0.5;
//		//找出结果集中距离查询最远的一个对象，并找出此对象的关键字
//		double maxDistance = 0.0;
//		double temp = 0.0;
//
//		//po[i].distance即节点与查询点之间的距离
//
//		double qx = newTest.coord[0];
//		double qy = newTest.coord[1];
//
//		for(int i=0;i<po.size();i++){
//
//			temp = Math.sqrt(Math.abs((x-qx)*(x-qx)-(y-qy)*(y-qy)));
//			temp = Calcu_dist()
//			if(temp > maxDistance)
//			{
//				maxDistance = temp;
//			}
//		}
//
//		List<Double> Result_queue = new ArrayList<Double>();
//
//		double maxDisObj = 0.0;
//		double temp2 = 0.0;
//		if(po.size() > 1)
//		{
//			for(int i=0;i<po.size()-1;i++){
//				for(int j=i+1;j<po.size();j++){
//					double x1 = po.get(i).getLatitude();
//					double y1 = po.get(i).getLongitude();
//
//					double x2 = po.get(j).getLatitude();
//					double y2 = po.get(j).getLongitude();
//
//					double distobj = Math.sqrt(Math.abs((x1-x2)*(x1-x2)-(y1-y2)*(y1-y2)));
//
//					if(temp2 > maxDisObj)
//					{
//						maxDisObj = temp2;
//					}
////					Result_queue.add(distobj);//求出对象间的距离并存储起来
//				}
//			}
//		}
//
//		double Cost = a*maxDistance + (1-a)*maxDisObj;//最终的Cost是查询与对象之家的最大距离与对象之间的最大距离的和
//
//		return Cost;
//	}
	//========================================================================================================

//	public List<IRPoint> CollectiveQuery(final List<IShape> query, List<String> keyword, final IVisitor v) {
//		List<IRPoint> po = new ArrayList<IRPoint>();//结果集
//
//		for(int i=0;i<query.size();i++)//确定查询集合中的每个查询点都是二维的
//		if (query.get(i).getDimension() != m_dimension) throw new IllegalArgumentException("nearestNeighborQuery: IShape dimensionality is incorrect.");
//
//		NNComparator nnc = new NNComparator();
//		po = CollectiveQuery(query, keyword, v, nnc);//调用下面的方法查找结果集
//		return po;
//	}

//	public List<IRPoint> CollectiveQuery(final List<IShape> query, List<String> keyword, final IVisitor v, final INearestNeighborComparator nnc) {
//		List<IRPoint> po = new ArrayList<IRPoint>();//作为最后的查询结果集
//
//		try
//		{
//			ArrayList queue = new ArrayList();//相当于Type2Appro1中的U
//
//			Node n = readNode(m_rootID);
//			queue.add(new NNEntry(n, 0.0));//在这里的NNEntry中包含了节点和距离，与U.Enqueue(irTree.root,0)是一样的效果
//
//			ArrayList Result_queue = new ArrayList();//用于存储查询与对象点之间的距离
//
//			//keyword在这里是一个未被覆盖的关键字集合
//			while (queue.size() != 0)//while U is not empty do
//			{
//				NNEntry first = (NNEntry) queue.remove(0);//e = U.Dequeue();
//
//				if (first.m_pEntry instanceof Node)//如果first.m_pEntry是一个节点
//				{
//					n = (Node) first.m_pEntry;
//					v.visitNode((INode) n);//访问节点n
//
//					for (int cChild = 0; cChild < n.m_children; cChild++)//(foreach entry e' in node e do)
//					{
//						if(n.m_pData != null)
//						{
//							IEntry e;
//							//在加入节点的过程中对节点的位置做排序
//							if (n.m_level == 0) {
//								String temp = new String(n.m_pData[cChild]);
//
//								if(keyword.contains(temp))
//								{
//									e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
//
//									double d = 0;
//									for(int i=0;i<query.size();i++){
//										if(d < nnc.getMinimumDistance(query.get(i), e))
//											d = nnc.getMinimumDistance(query.get(i), e);//d是第i个查询点与此叶子节点的所有最小距离中的最大距离
//									}
//									NNEntry e2 = new NNEntry(e, d);
//
//									queue.add(e2);
//								}
//							} else {
//								e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点
//
//								double d = 0;
//								double d2 = 0;
//								for(int i=0;i<query.size();i++){
//									if(d < nnc.getMinimumDistance(query.get(i), e))
//										d = nnc.getMinimumDistance(query.get(i), e);
//									if(d2<d)
//										d2=d;
//								}//找出距离查询点的最大距离
//								NNEntry e2 = new NNEntry(e, d);
//								System.out.println("距离为："+d2);
//
//								queue.add(e2);
//							}
//						}
//					}
//				}
//				else
//				{
//					IEntry e = first.m_pEntry;
//					if(keyword.size() == 0)//当所有的关键字都已经被查询到时，结束查询
//						break;
//
////					System.out.println("移除前的关键字集合为："+ keyword);
//
//					IRPoint temp = new IRPoint();
//					temp.m_pointID = first.m_pEntry.getIdentifier();
////					temp.Keyword = v.visitData((IData) first.m_pEntry);
//
////					System.out.println("temp.Keyword的值为："+ temp.Keyword);
//
//
////					if(keyword.contains(temp.Keyword))//在此步找到结果集
////					{
////						double d = 0;
////						double d2 = 0;
////						for(int i=0;i<query.size();i++){
////							if(d < nnc.getMinimumDistance(query.get(i), e))//一个结果对象与全部查询点之间的
////								d = nnc.getMinimumDistance(query.get(i), e);
////							if(d2<d)
////								d2=d;
////						}
////
////						temp.distance = d2;
//////						System.out.println("true 在此步去除一个关键字");
////						po.add(temp);
////						keyword.remove(temp.Keyword);
////					}
////					else
////					{
////						continue;
////					}
//					m_stats.m_queryResults++;
//
//					System.out.println("关键字集合为："+ keyword);
//					System.out.println();
//				}
//			}
//			System.out.println("查询结束");
//		}
//		finally
//		{
//			m_rwLock.read_unlock();
//		}
//
//		System.out.println("结果集的长度为："+ po.size());
//		double mincost = Calcu_dist(po,query);
//		System.out.println("最终结果集对应的最小距离代价为："+ mincost);
//		return po;
//	}

//	public List<IRPoint> CollectiveQuery_Type2(final List<IShape> query, List<String> keyword, final IVisitor v) {
//		List<IRPoint> po = new ArrayList<IRPoint>();
//
//		for(int i=0;i<query.size();i++)
//		if (query.get(i).getDimension() != m_dimension) throw new IllegalArgumentException("nearestNeighborQuery: IShape dimensionality is incorrect.");
//
//		NNComparator nnc = new NNComparator();
//		po = CollectiveQuery_Type2(query, keyword, v, nnc);
//		return po;
//	}

//	public List<IRPoint> CollectiveQuery_Type2(final List<IShape> query, List<String> keyword, final IVisitor v, final INearestNeighborComparator nnc) {
//		List<IRPoint> po = new ArrayList<IRPoint>();
//
//		try
//		{
//			ArrayList queue = new ArrayList();
//
//			Node n = readNode(m_rootID);
//			queue.add(new NNEntry(n, 0.0));
//
//			po = CollectiveQuery(query,keyword,v);//类型1得到的结果
//
//			double Cost = getCost(po);
//
//			//找出结果集当中最远的节点中包含的关键字
//
//			while (queue.size() != 0)
//			{
//				NNEntry first = (NNEntry) queue.remove(0);
//
//				if (first.m_pEntry instanceof Node)//if e is not an object then
//				{
//					n = (Node) first.m_pEntry;
//					v.visitNode((INode) n);//访问节点n
//
//					//确保结果集中的点包含的distance是存储对象到多个查询点之间的最小距离
//					//在这里找出最小距离中的最大距离
//					double maxDistance = po.get(0).distance;
//					for(int i=1;i<po.size();i++){
//						if(maxDistance < po.get(i).distance)
//							maxDistance = po.get(i).distance;
//					}
//
//					if(maxDistance >= Cost)
//						break;
//
//					for (int cChild = 0; cChild < n.m_children; cChild++)//(foreach entry e' in node e do)
//					{
//						if(n.m_pData != null)
//						{
//							IEntry e;
//							//在加入节点的过程中对节点的位置做排序
//							if (n.m_level == 0) {
//								String temp = new String(n.m_pData[cChild]);
//
//								if(keyword.contains(temp))
//								{
//									e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
//
//									double d = 0;
//									for(int i=0;i<query.size();i++){
//										if(d < nnc.getMinimumDistance(query.get(i), e))
//											d = nnc.getMinimumDistance(query.get(i), e);
//									}
//									NNEntry e2 = new NNEntry(e, d);
//									//如果e在这里代表着对象，则表示查询点与此对象之间的最小距离；如果e在这里代表着节点，则表示查询点与此节点的MBR之间的距离
//
//									queue.add(e2);
//								}
//
//							} else {
//								e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点
//
//								double d = 0;
//								for(int i=0;i<query.size();i++){
//									if(d < nnc.getMinimumDistance(query.get(i), e))
//										d = nnc.getMinimumDistance(query.get(i), e);
//								}
//								NNEntry e2 = new NNEntry(e, d);
//
//								queue.add(e2);
//							}
//						}
//					}
//				}
//				else
//				{
//					//求出查询集合与对象之间的距离的最大值
//					IShape s = first.m_pEntry.getShape();
//					double Dist=0;
//					for(int i=0;i<query.size();i++)
//					{
//						if(Dist < query.get(i).getMinimumDistance(s))
//						Dist = query.get(i).getMinimumDistance(s);
//					}
//
//					if(Dist >= Cost)
//						break;
//
//					IRPoint qe = new IRPoint();
//					List<IShape> querye = query;
//
//					List<IRPoint> poe = CollectiveQuery(querye,keyword,v);;
//
//					double Coste = getCost(poe);
//
//					if(Coste<Cost){//如果找到了所需Cost更少的一组对象，那么更新Cost值和最优结果集。
//						Cost = Coste;
//						po = poe;
//					}
//				}
//			}
//		}
//		finally
//		{
//			m_rwLock.read_unlock();
//		}
//
//		double mincost = getCost(po);
//		System.out.println("最终结果集对应的最小距离代价为："+ mincost);
//		return po;
//	}
	
	
	/**
	 * 返回所有的objects,因为多个bucket，所以是多组object
	 */
	public List<IRPoint> CollectiveQuery_temp(final IShape query, ArrayList<ArrayList<String>> resultlist, final IVisitor v) {
		List<IRPoint> po;
		List<IRPoint> poe = new ArrayList<>();
		double cost = Double.MAX_VALUE;

		System.out.println("总体循环次数："+resultlist.size());
		for (int i=0; i<resultlist.size(); i++){
//			System.out.println("第"+i+"次循环");
			po = CollectiveQuery_temp(query,resultlist.get(i),v,new NNComparator());

			if (po != null) {
				if (comp_cost(po, query) < cost) {
					cost = comp_cost(po, query);
					poe = po;
				}
			}
		}

		return poe;
	}

	class Entry{
		public int id;
		public boolean isLeaf;

		public Entry(int id,boolean isLeaf) {
			this.id = id;
			this.isLeaf = isLeaf;
		}
	}

	public List<IRPoint> CollectiveQuery_temp(final IShape query, ArrayList<String> buckids, final IVisitor v, final INearestNeighborComparator nnc) {
		List<IRPoint> po = new ArrayList<>();//作为最后的查询结果集
		List<IRPoint> poe = new ArrayList<>();
		double cost,coste;
		HashMap<String, HashSet> hash = new HashMap<String, HashSet>();
		ArrayList<String> tempbuckids = buckids;
		String key = new String();
		ArrayList queue = new ArrayList();//相当于Type2Appro1中的U
		IRPoint p2;

		try
		{
			Node n = readNode(m_rootID);
			queue.add(new NNEntry(n, 0.0));

			poe = Type2Appro1(query,buckids,v);
			coste = comp_cost(poe,query);
			p2 = comp_farthest_key(poe,query);

			for (int i=0; i<buckids.size(); i++){
				if (p2.bucketnumber.contains(buckids.get(i))){
					key = buckids.get(i);
					break;
				}
			}

			while(!queue.isEmpty()){
				NNEntry cn = (NNEntry) queue.remove(0);

				if (cn.m_pEntry instanceof Node) {

					if (nnc.getMinimumDistance(query, cn.m_pEntry) >= coste){
						break;
					}

					n = (Node) cn.m_pEntry;

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						IEntry e;

						if (n.m_level == 0)
						{
							e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
							NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(query, e));
							queue.add(e2);
						}
						else
						{
							e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点
							NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(query, e));
							queue.add(e2);
						}
					}
				}else{
					IRPoint p = newTest.point[cn.m_pEntry.getIdentifier()-1];
					if (!p.bucketnumber.contains(key))
						break;

					if (Calcu_dist(p,query)>=coste)
						break;

					IShape queryPoint = new Point(p.m_pCoords);

					po = Type2Appro1(queryPoint,buckids,v);
					cost = comp_cost(po,query);

					if (cost<coste){
						coste = cost;
						poe = po;
					}
				}
			}
		}
		finally
		{
			m_rwLock.read_unlock();
		}

		return poe;
	}

	public List<IRPoint> Type2Appro1(IShape queryPoint, ArrayList<String> buckids, IVisitor v) {
		List<IRPoint> po = new ArrayList<>();
		po = Type2Appro1(queryPoint,buckids,v,new NNComparator());
		return po;
	}

	public List<IRPoint> Type2Appro1(IShape queryPoint, ArrayList<String> buckids, IVisitor v, final INearestNeighborComparator nnc) {
		List<IRPoint> po = new ArrayList<>();//作为最后的查询结果集
		ArrayList<String> tempbuckids = buckids;
		ArrayList queue = new ArrayList();//相当于Type2Appro1中的U

		try
		{
			Node n = readNode(m_rootID);
			queue.add(new NNEntry(n, 0.0));

			while(!queue.isEmpty()){
				NNEntry cn = (NNEntry) queue.remove(0);

				if (cn.m_pEntry instanceof Node) {
					n = (Node) cn.m_pEntry;

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						IEntry e;

						if (n.m_level == 0)
						{
							e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
						}
						else
						{
							e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点
						}
						NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(queryPoint, e));
						queue.add(e2);
					}
				}else{
					IRPoint p = newTest.point[cn.m_pEntry.getIdentifier()-1];

					for (int i=0; i<tempbuckids.size();i++){
						if (p.bucketnumber.contains(tempbuckids.get(i))){
							po.add(p);
							tempbuckids.remove(i);
							break;
						}
					}

					if (tempbuckids.isEmpty())
						break;
				}
			}
		}
		finally
		{
			m_rwLock.read_unlock();
		}

		return po;
	}

	private IRPoint comp_farthest_key(List<IRPoint> S, IShape queryPoint) {
		double far_dist = 0,dist;
		IRPoint p = new IRPoint();

		for (int i=0; i<S.size(); i++){
			dist = Calcu_dist(S.get(i),queryPoint);
			if (dist > far_dist) {
				far_dist = dist;
				p = S.get(i);
			}
		}

		return p;
	}

//	public List<List<IRPoint>> CollectiveQuery_temp(final IShape query, List<String> buckids, final IVisitor v, final INearestNeighborComparator nnc) {
//		List<List<IRPoint>> po = new ArrayList<>();//作为最后的查询结果集
//		HashMap<String, HashSet> hash = new HashMap<String, HashSet>();//key:buckid,value:objectids
//		try
//		{
//			ArrayList queue = new ArrayList();//相当于Type2Appro1中的U
//			queue.add(new Entry(n.m_identifier, n.isLeaf()));
//			while(!queue.isEmpty()){
//				Entry cn = (Entry) queue.remove(0);
////				System.out.println("current node id:" + cn.id);
//
//				if(!cn.isLeaf){//非叶子节点
////					System.out.println("current node is not leaf node");
//					HashMap<String,ArrayList> invert = newTest.inverted.get(cn.id);
//					Iterator<String> iterator = invert.keySet().iterator();
////					System.out.print("inverted keys:" );
////					while(iterator.hasNext()){
////						String next = iterator.next();
////						System.out.print("" + next+",");
////					}
////					System.out.println();
//					for(int i=0;i<buckids.size();++i){
////						System.out.println("to find buckid:"+buckids.get(i));
//						if(invert.containsKey(buckids.get(i))){//有这个buckid
////							System.out.println("found");
//							List l = invert.get(buckids.get(i));
//							for(int k=0;k<l.size();++k){
//								int id = (int) l.get(k);
//								Node n2 = readNode(id);
//								queue.add(new Entry(n2.m_identifier, n2.isLeaf()));
////								System.out.println("add node");
//							}
//						}
//					}
//				}else{//叶子结点
//					HashMap<String,ArrayList> invert = newTest.inverted.get(cn.id);//存的是buckidid和objectids
//
//					for(int i=0;i<buckids.size();++i){
//						if(invert.containsKey(buckids.get(i))){//有这个buckid
//							String next = buckids.get(i);
//							if(!hash.containsKey(next)){
//								hash.put(next, new HashSet<Integer>());
//							}
//							hash.get(next).addAll(invert.get(next));
//						}
//					}
//				}
//			}
//
//			double knearest = 0.0;
//
//			Iterator<String> iterator = hash.keySet().iterator();
//			while(iterator.hasNext()){
//				String next = iterator.next();//bucketid
//				HashSet hashSet = hash.get(next);//objectids
//				ArrayList<IRPoint> objects = new ArrayList<IRPoint>();
//				Iterator iterator2 = hashSet.iterator();
//				while(iterator2.hasNext()){
//					int next2 = (int) iterator2.next();
//					objects.add(newTest.map.get(next2));
//				}
//				po.add(objects);
//			}
//
//		}
//		finally
//		{
//			m_rwLock.read_unlock();
//		}
//
//		//System.out.println("结果集的长度为："+ po.size());
//		return po;
//	}
	
//	public List<IRPoint> CollectiveQuery_Type3(final List<IShape> query, List<String> keyword, final IVisitor v) {
//		List<IRPoint> po = new ArrayList<IRPoint>();
//
//		for(int i=0;i<query.size();i++)
//		if (query.get(i).getDimension() != m_dimension) throw new IllegalArgumentException("nearestNeighborQuery: IShape dimensionality is incorrect.");
//
//		NNComparator nnc = new NNComparator();
//		po = CollectiveQuery_Type3(query, keyword, v, nnc);
//		return po;
//	}
	
//	public List<IRPoint> CollectiveQuery_Type3(final List<IShape> query, List<String> keyword, final IVisitor v, final INearestNeighborComparator nnc) {
//		List<IRPoint> po = new ArrayList<IRPoint>();
//		List<IRPoint> poe = new ArrayList<IRPoint>();
//
//		try
//		{
//			ArrayList queue = new ArrayList();
//
//			Node n = readNode(m_rootID);
//			queue.add(new NNEntry(n, 0.0));
//
//			List<String> keyword2 = new ArrayList<String>(keyword);
////			po = CollectiveQuery_temp(query.get(0),keyword2,v);
//
//			double Cost = getCost(po);
//			double Coste = Cost;
//
//			if (query.size() > 0)
//			{
//				for(int i=1;i<query.size();i++)
//				{
//					List<String> keyword3 = new ArrayList<String>(keyword);
////					poe = CollectiveQuery_temp(query.get(i),keyword3,v);
//					Coste = getCost(poe);
//
//					if(Coste < Cost)
//					{
//						po = poe;
//						Cost = Coste;
//					}
//				}
//			}
//		}
//		finally
//		{
//			m_rwLock.read_unlock();
//		}
//
//		double mincost = getCost(po);
//		System.out.println("最终结果集对应的最小距离代价为："+ mincost);
//		return po;
//	}
	
	//-------------------------------------MoSKQ-----------------------------------------------------------
	
//	public List<IRPoint> MoSKQ(final ArrayList<String> query, final IVisitor v) {
//		List<IRPoint> po = new ArrayList<IRPoint>();
//
//		NNComparator nnc = new NNComparator();
////		System.out.println("进入子查询MoSKQ!");
//		po = MoSKQ(query, v, nnc);
////		System.out.println("结束子查询MoSKQ!");
//		return po;
//	}
	
//	public List<IRPoint> MoSKQ(final ArrayList<String> query, final IVisitor v, final INearestNeighborComparator nnc) {
//		ArrayList<IRPoint> po = new ArrayList<IRPoint>();
//		List<IRPoint> poe = new ArrayList<IRPoint>();
//
//		try
//		{
////			long time1 = System.currentTimeMillis();
//			ArrayList queue = new ArrayList();
//
//			ArrayList<ArrayList<Integer>> queryList = new ArrayList<ArrayList<Integer>>();
//			ArrayList<ArrayList<Integer>> resulttemp = new ArrayList<ArrayList<Integer>>();
//			ArrayList<Integer> temp = new ArrayList<Integer>();
//
//			for(int count=0 ; count<query.size(); count++){
//				temp = n.m_invertedList.get(query.get(count));//temp代表一个桶内的一组id
//				queryList.add(temp);//将querylist看做一个二维数组，每一行存储一组id，一共有K列，K表示关键字
//			}
////			System.gc();
////			System.out.println("进入MoSKQ函数中的递归！");
//			//在这里的递归是通过传入query对应的二维的一个桶号的数组，找出全部的结果集的桶号的组合可能
//			resulttemp = permutation(queryList);
////			System.out.println("结束MoSKQ函数中的递归！");
//
//			double cost = Double.MAX_VALUE;
//			double costtemp = 0.0;
////			System.out.println("结果集的大小为:" + resulttemp.size());
//			for(int itemp=0 ; itemp<resulttemp.size(); itemp++){
//				for(int jtemp=0 ; jtemp<resulttemp.get(itemp).size() ; jtemp++){
//					//已知一个空间文本对象的ID，如何获取其经纬度，并计算与查询点之间的距离
//					po.add(newTest.map.get(resulttemp.get(itemp).get(jtemp)));
//				}
////				System.gc();
////				System.out.println("计算结果集的距离代价！"+itemp);
//				costtemp = getCost(po);
//				if(cost > costtemp)
//				{
//					cost = costtemp;
//					Object clone = po.clone();
//					poe = (List<IRPoint>) clone;
//
//				}
//				po.clear();
//			}
////			System.gc();
////			System.out.println("结束MoSKQ算法！");
////			long time2 = System.currentTimeMillis();
////			System.out.println("MOSKQ执行一次的时间" + (time2-time1));
//		}
//		finally
//		{
//			m_rwLock.read_unlock();
//		}
//
//		return poe;
//	}
	
	public static ArrayList<ArrayList<Integer>> permutation(ArrayList<ArrayList<Integer>> inputList){
		ArrayList<ArrayList<Integer>> resList = new ArrayList<ArrayList<Integer>>();
		ArrayList<Integer> arr = new ArrayList<Integer>();
		for(int i=0; i<inputList.size(); i++){
			arr.add(0);
		}
	    permutationInt(inputList, resList, 0, arr);//new char[inputList.size()] -> new ArrayList<String>() 
	    return resList;
	}

	public static void permutationInt(ArrayList<ArrayList<Integer>> inputList, ArrayList<ArrayList<Integer>> resList, int ind, ArrayList<Integer> arr) {
	    if(ind == inputList.size()){
	        Object clone = arr.clone();
			resList.add((ArrayList<Integer>) clone);
	        return;
	    }

	    for(int c: inputList.get(ind)){
	        arr.set(ind, c);
	        permutationInt(inputList, resList, ind + 1, arr);
	    }
	}
	
	//-----------------------------------------------------------------------------------------------------

	public void queryStrategy(final IQueryStrategy qs) {
		m_rwLock.read_lock();

		Integer next = new Integer(m_rootID);

		try
		{
			while (true)
			{
				Node n = readNode(next.intValue());
				Boolean hasNext = new Boolean(false);
				qs.getNextEntry(n, next, hasNext);
				if (hasNext.booleanValue() == false) break;
			}
		}
		finally
		{
			m_rwLock.read_unlock();
		}
	}

	public PropertySet getIndexProperties() {
		PropertySet pRet = new PropertySet();

		// dimension
		pRet.setProperty("Dimension", new Integer(m_dimension));

		// index capacity
		pRet.setProperty("IndexCapacity", new Integer(m_indexCapacity));

		// leaf capacity
		pRet.setProperty("LeafCapacity", new Integer(m_leafCapacity));

		// R-tree variant
		pRet.setProperty("TreeVariant", new Integer(m_treeVariant));

		// fill factor
		pRet.setProperty("FillFactor", new Double(m_fillFactor));

		// near minimum overlap factor
		pRet.setProperty("NearMinimumOverlapFactor", new Integer(m_nearMinimumOverlapFactor));

		// split distribution factor
		pRet.setProperty("SplitDistributionFactor", new Double(m_splitDistributionFactor));

		// reinsert factor
		pRet.setProperty("ReinsertFactor", new Double(m_reinsertFactor));

		return pRet;
	}

	public void addWriteNodeCommand(INodeCommand nc)
	{
		m_writeNodeCommands.add(nc);
	}

	public void addReadNodeCommand(INodeCommand nc)
	{
		m_readNodeCommands.add(nc);
	}

	public void addDeleteNodeCommand(INodeCommand nc)
	{
		m_deleteNodeCommands.add(nc);
	}

	public boolean isIndexValid() {
		boolean ret = true;
		Stack st = new Stack();
		Node root = readNode(m_rootID);

		if (root.m_level != m_stats.m_treeHeight - 1)
		{
			System.err.println("Invalid tree height");
			return false;
		}

		HashMap nodesInLevel = new HashMap();
		nodesInLevel.put(new Integer(root.m_level), new Integer(1));

		ValidateEntry e = new ValidateEntry(root.m_nodeMBR, root);
		st.push(e);

		while (! st.empty())
		{
			e = (ValidateEntry) st.pop();

			Region tmpRegion = (Region) m_infiniteRegion.clone();

			for (int cDim = 0; cDim < m_dimension; cDim++)
			{
				tmpRegion.m_pLow[cDim] = Double.POSITIVE_INFINITY;
				tmpRegion.m_pHigh[cDim] = Double.NEGATIVE_INFINITY;

				for (int cChild = 0; cChild < e.m_pNode.m_children; cChild++)
				{
					tmpRegion.m_pLow[cDim] = Math.min(tmpRegion.m_pLow[cDim], e.m_pNode.m_pMBR[cChild].m_pLow[cDim]);
					tmpRegion.m_pHigh[cDim] = Math.max(tmpRegion.m_pHigh[cDim], e.m_pNode.m_pMBR[cChild].m_pHigh[cDim]);
				}
			}

			if (! (tmpRegion.equals(e.m_pNode.m_nodeMBR)))
			{
				System.err.println("Invalid parent information");
				ret = false;
			}
			else if (! (tmpRegion.equals(e.m_parentMBR)))
			{
				System.err.println("Error in parent");
				ret = false;
			}

			if (e.m_pNode.m_level != 0)
			{
				for (int cChild = 0; cChild < e.m_pNode.m_children; cChild++)
				{
					ValidateEntry tmpEntry = new ValidateEntry(e.m_pNode.m_pMBR[cChild], readNode(e.m_pNode.m_pIdentifier[cChild]));

					if (! nodesInLevel.containsKey(new Integer(tmpEntry.m_pNode.m_level)))
					{
						nodesInLevel.put(new Integer(tmpEntry.m_pNode.m_level), new Integer(1));
					}
					else
					{
						int i = ((Integer) nodesInLevel.get(new Integer(tmpEntry.m_pNode.m_level))).intValue();
						nodesInLevel.put(new Integer(tmpEntry.m_pNode.m_level), new Integer(i + 1));
					}

					st.push(tmpEntry);
				}
			}
		}

		int nodes = 0;
		for (int cLevel = 0; cLevel < m_stats.m_treeHeight; cLevel++)
		{
			int i1 = ((Integer) nodesInLevel.get(new Integer(cLevel))).intValue();
			int i2 = ((Integer) m_stats.m_nodesInLevel.get(cLevel)).intValue();
			if (i1 != i2)
			{
				System.err.println("Invalid nodesInLevel information");
				ret = false;
			}

			nodes += i2;
		}

		if (nodes != m_stats.m_nodes)
		{
			System.err.println("Invalid number of nodes information");
			ret = false;
		}

		return ret;
	}

	public IStatistics getStatistics()
	{
		return (IStatistics) m_stats.clone();
	}

	public void flush() throws IllegalStateException {
		try
		{
			storeHeader();
			m_pStorageManager.flush();
		}
		catch (IOException e)
		{
			System.err.println(e);
			throw new IllegalStateException("flush failed with IOException");
		}
	}

	//
	// Internals
	//

	private void initNew(PropertySet ps) throws IOException {
		Object var;

		// tree variant.
		var = ps.getProperty("TreeVariant");
		if (var != null)
		{
			if (var instanceof Integer)
			{
				int i = ((Integer) var).intValue();
				if (i != SpatialIndex.RtreeVariantLinear &&  i != SpatialIndex.RtreeVariantQuadratic && i != SpatialIndex.RtreeVariantRstar)
					throw new IllegalArgumentException("Property TreeVariant not a valid variant");
				m_treeVariant = i;
			}
			else
			{
				throw new IllegalArgumentException("Property TreeVariant must be an Integer");
			}
		}

		// fill factor.
		var = ps.getProperty("FillFactor");
		if (var != null)
		{
			if (var instanceof Double)
			{
				double f = ((Double) var).doubleValue();
				if (f <= 0.0f || f >= 1.0f)
					throw new IllegalArgumentException("Property FillFactor must be in (0.0, 1.0)");
				m_fillFactor = f;
			}
			else
			{
				throw new IllegalArgumentException("Property FillFactor must be a Double");
			}
		}

		// index capacity.
		var = ps.getProperty("IndexCapacity");
		if (var != null)
		{
			if (var instanceof Integer)
			{
				int i = ((Integer) var).intValue();
				if (i < 3) throw new IllegalArgumentException("Property IndexCapacity must be >= 3");
				m_indexCapacity = i;
			}
			else
			{
				throw new IllegalArgumentException("Property IndexCapacity must be an Integer");
			}
		}

		// leaf capacity.
		var = ps.getProperty("LeafCapacity");
		if (var != null)
		{
			if (var instanceof Integer)
			{
				int i = ((Integer) var).intValue();
				if (i < 3) throw new IllegalArgumentException("Property LeafCapacity must be >= 3");
				m_leafCapacity = i;
			}
			else
			{
				throw new IllegalArgumentException("Property LeafCapacity must be an Integer");
			}
		}

		// near minimum overlap factor.
		var = ps.getProperty("NearMinimumOverlapFactor");
		if (var != null)
		{
			if (var instanceof Integer)
			{
				int i = ((Integer) var).intValue();
				if (i < 1 || i > m_indexCapacity || i > m_leafCapacity)
					throw new IllegalArgumentException("Property NearMinimumOverlapFactor must be less than both index and leaf capacities");
			m_nearMinimumOverlapFactor = i;
			}
			else
			{
				throw new IllegalArgumentException("Property NearMinimumOverlapFactor must be an Integer");
			}
		}

		// split distribution factor.
		var = ps.getProperty("SplitDistributionFactor");
		if (var != null)
		{
			if (var instanceof Double)
			{
				double f = ((Double) var).doubleValue();
				if (f <= 0.0f || f >= 1.0f)
					throw new IllegalArgumentException("Property SplitDistributionFactor must be in (0.0, 1.0)");
				m_splitDistributionFactor = f;
			}
			else
			{
				throw new IllegalArgumentException("Property SplitDistriburionFactor must be a Double");
			}
		}

		// reinsert factor.
		var = ps.getProperty("ReinsertFactor");
		if (var != null)
		{
			if (var instanceof Double)
			{
				double f = ((Double) var).doubleValue();
				if (f <= 0.0f || f >= 1.0f)
					throw new IllegalArgumentException("Property ReinsertFactor must be in (0.0, 1.0)");
				m_reinsertFactor = f;
			}
			else
			{
				throw new IllegalArgumentException("Property ReinsertFactor must be a Double");
			}
		}

		// dimension
		var = ps.getProperty("Dimension");
		if (var != null)
		{
			if (var instanceof Integer)
			{
				int i = ((Integer) var).intValue();
				if (i <= 1) throw new IllegalArgumentException("Property Dimension must be >= 1");
				m_dimension = i;
			}
			else
			{
				throw new IllegalArgumentException("Property Dimension must be an Integer");
			}
		}

		m_infiniteRegion.m_pLow = new double[m_dimension];
		m_infiniteRegion.m_pHigh = new double[m_dimension];

		for (int cDim = 0; cDim < m_dimension; cDim++)
		{
			m_infiniteRegion.m_pLow[cDim] = Double.POSITIVE_INFINITY;
			m_infiniteRegion.m_pHigh[cDim] = Double.NEGATIVE_INFINITY;
		}

		m_stats.m_treeHeight = 1;
		m_stats.m_nodesInLevel.add(new Integer(0));

		Leaf root = new Leaf(this, -1);
		m_rootID = writeNode(root);

		storeHeader();
	}

	private void initOld(PropertySet ps) throws IOException {
		loadHeader();

		// only some of the properties may be changed.
		// the rest are just ignored.

		Object var;

		// tree variant.
		var = ps.getProperty("TreeVariant");
		if (var != null)
		{
			if (var instanceof Integer)
			{
				int i = ((Integer) var).intValue();
				if (i != SpatialIndex.RtreeVariantLinear &&  i != SpatialIndex.RtreeVariantQuadratic && i != SpatialIndex.RtreeVariantRstar)
					throw new IllegalArgumentException("Property TreeVariant not a valid variant");
				m_treeVariant = i;
			}
			else
			{
				throw new IllegalArgumentException("Property TreeVariant must be an Integer");
			}
		}

		// near minimum overlap factor.
		var = ps.getProperty("NearMinimumOverlapFactor");
		if (var != null)
		{
			if (var instanceof Integer)
			{
				int i = ((Integer) var).intValue();
				if (i < 1 || i > m_indexCapacity || i > m_leafCapacity)
					throw new IllegalArgumentException("Property NearMinimumOverlapFactor must be less than both index and leaf capacities");
				m_nearMinimumOverlapFactor = i;
			}
			else
			{
				throw new IllegalArgumentException("Property NearMinimumOverlapFactor must be an Integer");
			}
		}

		// split distribution factor.
		var = ps.getProperty("SplitDistributionFactor");
		if (var != null)
		{
			if (var instanceof Double)
			{
				double f = ((Double) var).doubleValue();
				if (f <= 0.0f || f >= 1.0f)
					throw new IllegalArgumentException("Property SplitDistributionFactor must be in (0.0, 1.0)");
				m_splitDistributionFactor = f;
			}
			else
			{
				throw new IllegalArgumentException("Property SplitDistriburionFactor must be a Double");
			}
		}

		// reinsert factor.
		var = ps.getProperty("ReinsertFactor");
		if (var != null)
		{
			if (var instanceof Double)
			{
				double f = ((Double) var).doubleValue();
				if (f <= 0.0f || f >= 1.0f)
					throw new IllegalArgumentException("Property ReinsertFactor must be in (0.0, 1.0)");
				m_reinsertFactor = f;
			}
			else
			{
				throw new IllegalArgumentException("Property ReinsertFactor must be a Double");
			}
		}

		m_infiniteRegion.m_pLow = new double[m_dimension];
		m_infiniteRegion.m_pHigh = new double[m_dimension];

		for (int cDim = 0; cDim < m_dimension; cDim++)
		{
			m_infiniteRegion.m_pLow[cDim] = Double.POSITIVE_INFINITY;
			m_infiniteRegion.m_pHigh[cDim] = Double.NEGATIVE_INFINITY;
		}
	}

	private void storeHeader() throws IOException {
		ByteArrayOutputStream bs = new ByteArrayOutputStream();
		DataOutputStream ds = new DataOutputStream(bs);

		ds.writeInt(m_rootID);
		ds.writeInt(m_treeVariant);
		ds.writeDouble(m_fillFactor);
		ds.writeInt(m_indexCapacity);
		ds.writeInt(m_leafCapacity);
		ds.writeInt(m_nearMinimumOverlapFactor);
		ds.writeDouble(m_splitDistributionFactor);
		ds.writeDouble(m_reinsertFactor);
		ds.writeInt(m_dimension);
		ds.writeLong(m_stats.m_nodes);
		ds.writeLong(m_stats.m_data);
		ds.writeInt(m_stats.m_treeHeight);

		for (int cLevel = 0; cLevel < m_stats.m_treeHeight; cLevel++)
		{
			ds.writeInt(((Integer) m_stats.m_nodesInLevel.get(cLevel)).intValue());
		}

		ds.flush();
		m_headerID = m_pStorageManager.storeByteArray(m_headerID, bs.toByteArray());
	}

	private void loadHeader() throws IOException {
		byte[] data = m_pStorageManager.loadByteArray(m_headerID);
		DataInputStream ds = new DataInputStream(new ByteArrayInputStream(data));

		m_rootID = ds.readInt();
		m_treeVariant = ds.readInt();
		m_fillFactor = ds.readDouble();
		m_indexCapacity = ds.readInt();
		m_leafCapacity = ds.readInt();
		m_nearMinimumOverlapFactor = ds.readInt();
		m_splitDistributionFactor = ds.readDouble();
		m_reinsertFactor = ds.readDouble();
		m_dimension = ds.readInt();
		m_stats.m_nodes = ds.readLong();
		m_stats.m_data = ds.readLong();
		m_stats.m_treeHeight = ds.readInt();

		for (int cLevel = 0; cLevel < m_stats.m_treeHeight; cLevel++)
		{
			m_stats.m_nodesInLevel.add(new Integer(ds.readInt()));
		}
	}

	protected void insertData_impl(byte[] pData, Region mbr, int id) {
		//assert mbr.getDimension() == m_dimension;

		boolean[] overflowTable;

		Stack pathBuffer = new Stack();

		Node root = readNode(m_rootID);

		overflowTable = new boolean[root.m_level];
		for (int cLevel = 0; cLevel < root.m_level; cLevel++) overflowTable[cLevel] = false;

		Node l = root.chooseSubtree(mbr, 0, pathBuffer);
		l.insertData(pData, mbr, id, pathBuffer, overflowTable);

		m_stats.m_data++;
	}

	protected void insertData_impl(byte[] pData, Region mbr, int id, int level, boolean[] overflowTable) {
		//assert mbr.getDimension() == m_dimension;

		Stack pathBuffer = new Stack();

		Node root = readNode(m_rootID);
		Node n = root.chooseSubtree(mbr, level, pathBuffer);
		n.insertData(pData, mbr, id, pathBuffer, overflowTable);
	}

	protected boolean deleteData_impl(final Region mbr, int id) {
		//assert mbr.getDimension() == m_dimension;

		boolean bRet = false;

		Stack pathBuffer = new Stack();

		Node root = readNode(m_rootID);
		Leaf l = root.findLeaf(mbr, id, pathBuffer);

		if (l != null)
		{
			l.deleteData(id, pathBuffer);
			m_stats.m_data--;
			bRet = true;
		}

		return bRet;
	}

	protected int writeNode(Node n) throws IllegalStateException {
		byte[] buffer = null;

		try
		{
			buffer = n.store();
		}
		catch (IOException e)
		{
			System.err.println(e);
			throw new IllegalStateException("writeNode failed with IOException");
		}

		int page;
		if (n.m_identifier < 0) page = IStorageManager.NewPage;
		else page = n.m_identifier;

		try
		{
			page = m_pStorageManager.storeByteArray(page, buffer);
		}
		catch (InvalidPageException e)
		{
			System.err.println(e);
			throw new IllegalStateException("writeNode failed with InvalidPageException");
		}

		if (n.m_identifier < 0)
		{
			n.m_identifier = page;
			m_stats.m_nodes++;
			int i = ((Integer) m_stats.m_nodesInLevel.get(n.m_level)).intValue();
			m_stats.m_nodesInLevel.set(n.m_level, new Integer(i + 1));
		}

		m_stats.m_writes++;

		for (int cIndex = 0; cIndex < m_writeNodeCommands.size(); cIndex++)
		{
			((INodeCommand) m_writeNodeCommands.get(cIndex)).execute(n);
		}

		return page;
	}
	
	protected Node readNode(int id) {
		byte[] buffer;
		DataInputStream ds = null;
		int nodeType = -1;
		Node n = null;
		String filename = "D:\\LSHdata\\InvertedList\\InvertedList" + id + ".txt";

		try
		{
			buffer = m_pStorageManager.loadByteArray(id);
			ds = new DataInputStream(new ByteArrayInputStream(buffer));
			nodeType = ds.readInt();

			if (nodeType == SpatialIndex.PersistentIndex) n = new Index(this, -1, 0);
			else if (nodeType == SpatialIndex.PersistentLeaf) n = new Leaf(this, -1);
			else throw new IllegalStateException("readNode failed reading the correct node type information");

			n.m_pTree = this;
			n.m_identifier = id;
			n.load(buffer);

			n.m_invertedList = readFile(filename);

			m_stats.m_reads++;
		}
		catch (InvalidPageException e)
		{
			System.err.println(e);
			throw new IllegalStateException("readNode failed with InvalidPageException");
		}
		catch (IOException e)
		{
			System.err.println(e);
			throw new IllegalStateException("readNode failed with IOException");
		}

		for (int cIndex = 0; cIndex < m_readNodeCommands.size(); cIndex++)
		{
			((INodeCommand) m_readNodeCommands.get(cIndex)).execute(n);
		}

		return n;
	}

	protected static HashMap<String,ArrayList<Integer>> readFile(String filename) throws IOException {
		String line = "";
		HashMap<String,ArrayList<Integer>> invertedlist = new HashMap<>();

		File file = new File(filename);
		if (!file.exists()){
//			System.out.println("不存在这个文件");
			return invertedlist;
		}
//		System.out.println("文件存在");
		BufferedReader br = new BufferedReader(new FileReader(filename));
		StringBuffer buffer1 = new StringBuffer();
		while ((line = br.readLine()) != null){
			buffer1.append(line);
		}

		String fileContent = buffer1.toString();
		Pattern p = Pattern.compile("\\d*=\\[.*?\\]");
		Matcher m = p.matcher(fileContent);
		while (m.find()) {
			String[] s =m.group().split("=");
			String key = s[0];
			ArrayList value = new ArrayList();
			String[] valuetemp = s[1].replace("[","").replace("]","").split(", ");
			for (int i=0; i<valuetemp.length; i++){
				int temp = Integer.parseInt(valuetemp[i]);
				value.add(temp);
			}
			invertedlist.put(key,value);
		}

		return invertedlist;
	}

	protected void deleteNode(Node n) {
		try
		{
			m_pStorageManager.deleteByteArray(n.m_identifier);
		}
		catch (InvalidPageException e)
		{
			System.err.println(e);
			throw new IllegalStateException("deleteNode failed with InvalidPageException");
		}

		m_stats.m_nodes--;
		int i = ((Integer) m_stats.m_nodesInLevel.get(n.m_level)).intValue();
		m_stats.m_nodesInLevel.set(n.m_level, new Integer(i - 1));

		for (int cIndex = 0; cIndex < m_deleteNodeCommands.size(); cIndex++)
		{
			((INodeCommand) m_deleteNodeCommands.get(cIndex)).execute(n);
		}
	}

	private void rangeQuery(int type, final IShape query, final IVisitor v) {
		m_rwLock.read_lock();

		try
		{
			Stack st = new Stack();
			Node root = readNode(m_rootID);

			if (root.m_children > 0 && query.intersects(root.m_nodeMBR)) st.push(root);

			while (! st.empty())
			{
				Node n = (Node) st.pop();

				if (n.m_level == 0)
				{
					v.visitNode((INode) n);

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						boolean b;
						if (type == SpatialIndex.ContainmentQuery) b = query.contains(n.m_pMBR[cChild]);
						else b = query.intersects(n.m_pMBR[cChild]);

						if (b)
						{
							Data data = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
							v.visitData(data);
							m_stats.m_queryResults++;
						}
					}
				}
				else
				{
					v.visitNode((INode) n);

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						if (query.intersects(n.m_pMBR[cChild]))
						{
							st.push(readNode(n.m_pIdentifier[cChild]));
						}
					}
				}
			}
		}
		finally
		{
			m_rwLock.read_unlock();
		}
	}

	public String toString() {
		String s = "Dimension: " + m_dimension + "\n"
						 + "Fill factor: " + m_fillFactor + "\n"
						 + "Index capacity: " + m_indexCapacity + "\n"
						 + "Leaf capacity: " + m_leafCapacity + "\n";

		if (m_treeVariant == SpatialIndex.RtreeVariantRstar)
		{
			s += "Near minimum overlap factor: " + m_nearMinimumOverlapFactor + "\n"
				 + "Reinsert factor: " + m_reinsertFactor + "\n"
				 + "Split distribution factor: " + m_splitDistributionFactor + "\n";
		}

		s += "Utilization: " + 100 * m_stats.getNumberOfData() / (m_stats.getNumberOfNodesInLevel(0) * m_leafCapacity) + "%" + "\n"
			 + m_stats;

		return s;
	}

	class NNEntry {
		IEntry m_pEntry;
		double m_minDist;

		NNEntry(IEntry e, double f) { m_pEntry = e; m_minDist = f; }
	}

	class NNEntryComparator implements Comparator {
		public int compare(Object o1, Object o2)
		{
			NNEntry n1 = (NNEntry) o1;
			NNEntry n2 = (NNEntry) o2;

			if (n1.m_minDist < n2.m_minDist) return -1;
			if (n1.m_minDist > n2.m_minDist) return 1;
			return 0;
		}
	}

	class NNComparator implements INearestNeighborComparator {
		public double getMinimumDistance(IShape query, IEntry e)
		{
			IShape s = e.getShape();
			return query.getMinimumDistance(s);
		}
	}

	class ValidateEntry {
		Region m_parentMBR;
		Node m_pNode;

		ValidateEntry(Region r, Node pNode) { m_parentMBR = r; m_pNode = pNode; }
	}

	class Data implements IData {
		int m_id;
		Region m_shape;
		byte[] m_pData;

		Data(byte[] pData, Region mbr, int id) { m_id = id; m_shape = mbr; m_pData = pData; }

		public int getIdentifier() { return m_id; }
		public IShape getShape() { return new Region(m_shape); }
		public byte[] getData()
		{
			byte[] data = new byte[m_pData.length];
			System.arraycopy(m_pData, 0, data, 0, m_pData.length);
			return data;
		}
	}
	
	
//	public static void main(String[] args){
//		ArrayList<ArrayList<Integer>> as = new ArrayList<>();
//		for(int i = 0;i<3;++i){
//			ArrayList<Integer> a = new ArrayList<Integer>();
//			a.add(i);
//			a.add(i+5);
//		}
//		permutation(as);
//	}

	public List<IRPoint> getSubsetInRadius(double radius, final IShape queryPoint, int queryCount) {
		List<IRPoint> po = getSubsetInRadius(radius,queryPoint,queryCount,new NNComparator());
		return po;
	}

	public List<IRPoint> getSubsetInRadius(double radius, final IShape queryPoint, int querycount, final INearestNeighborComparator nnc) {
		List<IRPoint> po = new ArrayList<IRPoint>();

		try
		{
			ArrayList queue = new ArrayList();//加入NNEntry <Node/Object,distance>
			ArrayList Result_queue = new ArrayList();
			int count = 0;
			double knearest = 0.0;

			Node n = readNode(m_rootID);
			queue.add(new NNEntry(n, 0.0));//加入根节点，距离为0

			while (queue.size() != 0)
			{
				NNEntry first = (NNEntry) queue.remove(0);//取出队列中的第一个元素

				if (first.m_pEntry instanceof Node)//如果first.m_pEntry是Node的一个实例
				{
					n = (Node) first.m_pEntry;
//					v.visitNode((INode) n);//访问节点n

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						IEntry e;

						if (n.m_level == 0)
						{
							e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
							//如果此节点是叶子节点，则依次读出叶子节点当中的对象的值

							//先判断query到MBR的最小距离是否在radius内，如果超过这个radius，那么就不加入队列
							//直到访问到object的时候，判断这个object中是否匹配查询关键字（桶号）

//							String word = new String(n.m_pData[cChild]);
								//如果此节点是叶节点，则找出此实例当中的关键字

							//如果此节点到MBR之间的最大距离大于radius，那么不需要将这些点加入结果集中
							if(nnc.getMinimumDistance(queryPoint, e) >= radius)
								continue;

							NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(queryPoint, e));
							//查询点与此节点的MBR之间的距离

							Result_queue.add(nnc.getMinimumDistance(queryPoint, e));//将关键字相同的点之间的距离存到数组当中
							
//							int loc = Collections.binarySearch(queue, e2, new NNEntryComparator());
//							if (loc >= 0) queue.add(loc, e2);//增加条件，使得查询到的符合条件的点的关键字与查询的关键字相同
//							else
							queue.add(e2);									
						}
						else
						{
							e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点

							if(nnc.getMinimumDistance(queryPoint, e) >= radius)
								continue;

							NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(queryPoint, e));
							
//							int loc = Collections.binarySearch(queue, e2, new NNEntryComparator());
//							if (loc >= 0) queue.add(loc, e2);
//							else 
							queue.add(e2);
						}
					}
				}
				else
				{
					if(first.m_minDist >= radius)
						continue;
//					v.visitData((IData) first.m_pEntry);

					IRPoint temp = new IRPoint();
					temp.m_pointID = first.m_pEntry.getIdentifier();
					//temp.Keyword = keyword;
//					temp.distance = first.m_minDist;

					po.add(temp);
					m_stats.m_queryResults++;
					count++;
					knearest = first.m_minDist;
//					System.out.println(first.m_minDist);
				}
			}
			
//			Collections.sort(Result_queue);
//			System.out.println("Key Distances:" + po);
		}
		finally
		{
			m_rwLock.read_unlock();
		}
		return po;
	}

	//递归求所有的子集
	void subsets(int queryCount, ArrayList<IRPoint> allObject, ArrayList<IRPoint> temp,int level, ArrayList<ArrayList<IRPoint>> result) {
//		  //如果是叶子节点则加入到result中
//		  if(level == queryCount)
//		  {
//		    result.push_back(temp);
//		    return;
//		  }
//
//		  subsets(queryCount,allObject,temp,level + 1,result);
//
//		  temp.push_back(S[level]);
//		  subsets(queryCount,allObject,temp,level + 1,result);
	}
		
//		public ArrayList<IRPoint> Replace(Point queryPoint, List<IRPoint> po)
//		{
//			ArrayList<IRPoint> result = new ArrayList<IRPoint>();
////			if (query.getDimension() != m_dimension) throw new IllegalArgumentException("TOPK: IShape dimensionality is incorrect.");
//			NNComparator nnc = new NNComparator();
//			result = Replace(queryPoint, po, nnc);
//			return result;
//		}
	
	public void Replace(Point queryPoint, List<IRPoint> po){
		List<IRPoint> result = new ArrayList<IRPoint>();
		IRPoint temp;
		double marginalGain=0;

		result = readallIRPoint(queryPoint);
		for(IRPoint irp: po) {

		}

	}
		
	public List<IRPoint> readallIRPoint(Point queryPoint) {
		List<IRPoint> result = new ArrayList<IRPoint>();
//			if (query.getDimension() != m_dimension) throw new IllegalArgumentException("TOPK: IShape dimensionality is incorrect.");
		NNComparator nnc = new NNComparator();
		result = readallIRPoint(queryPoint, nnc);
		return result;
	}

	public List<IRPoint> readallIRPoint(Point queryPoint, final INearestNeighborComparator nnc) {
		List<IRPoint> po = new ArrayList<IRPoint>();

		try
		{
			ArrayList queue = new ArrayList();
//				ArrayList Result_queue = new ArrayList();

			Node n = readNode(m_rootID);
			queue.add(new NNEntry(n, 0.0));//加入根节点，距离为0

//				int count = 0;

			while (queue.size() != 0)
			{
				NNEntry first = (NNEntry) queue.remove(0);

				if (first.m_pEntry instanceof Node)//如果first.m_pEntry是Node的一个实例
				{
					n = (Node) first.m_pEntry;

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						IEntry e;

						if (n.m_level == 0)
						{
							e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
						}
						else
						{
							e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点
						}
						NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(queryPoint, e));
						queue.add(e);
					}
				}
				else
				{
					IRPoint temp = new IRPoint();
					temp.m_pointID = first.m_pEntry.getIdentifier();
//					temp.distance = first.m_minDist;

					po.add(temp);
//						m_stats.m_queryResults++;
//						count++;
				}
			}
				
//				Collections.sort(Result_queue);
			System.out.println("point number:" + po.size());
		}
		finally
		{
			m_rwLock.read_unlock();
		}
		return po;
	}

	//2017.2.23 diameter distance cost approach strategy
	public List<IRPoint> DiameterMoSKQ(final IShape queryPoint, ArrayList<ArrayList<String>> resultlist) {
		List<IRPoint> po;
		List<IRPoint> poe = new ArrayList<>();
		double cost = Double.MAX_VALUE;

		System.out.println("总体循环次数："+resultlist.size());
		for (int i=0; i<resultlist.size(); i++){
			System.out.println("第"+i+"次循环");
			po = DiameterMoSKQ2(queryPoint,resultlist.get(i));

			if (!po.isEmpty()) {
				if (comp_cost(po, queryPoint) < cost) {
					cost = comp_cost(po, queryPoint);
					poe = po;
				}
			}
		}

		return poe;
	}

	public List<IRPoint> DiameterMoSKQ2(final IShape queryPoint, ArrayList<String> bucketnumber/*ArrayList<String> bucketnumber, double cost*/){
//		System.out.println("进入子查询");
		//----------------------------------------------------Parameter---------------------------------------------------------------------
		List<IRPoint> initS = new ArrayList<>();//当前最优的结果
		List<IRPoint> S = new ArrayList<>();//新构造的可行解
		double UB=0,LB=0,cost,cost_c,dist;//可行解S的上界和下界,initS的cost
		String ub = new String(),lb = new String();

		IRPoint o;//当前的object

		double dis_u,dis_l;//外径和内径
		List<Integer> R,region_u,region_l;//region R，外环内的所有object的id，内环内的所有object的id
		List<IRPoint> obj_pair;//<o_1,o_2>组合

		//-----------------------------------------------------Search------------------------------------------------------------------------
		//------------------------------------------得到初始集合并且计算上界下界-------------------------------------------------------------
//		System.out.println("comp_bounds");
		LB = comp_bounds(queryPoint, bucketnumber, initS);
		if (LB == 0){
			return null;
		}
//		System.out.println("comp_cost");
		UB = comp_cost(initS, queryPoint);
		cost_c = UB;
//		System.out.println("lower bound:"+LB);
//		System.out.println("upper bound:"+UB);
		if (initS.isEmpty()){
			return null;
		}


		//--------------------根据初始集合确定一个Region，并且将Region中的对象按照其与查询点之间的距离排序---------------------------------
		/*
		* 初始化一个region称为R
		* 所有选择的可能都在这个区域中
		* 在这个环内做range query
		* */
//		System.out.println("getObjectIdInRadius_for_upperbound");
		region_u = getObjectIdInRadius(UB, queryPoint, 1, bucketnumber);//注意包含下界上的值
//		System.out.println(region_u.size());
//		System.out.println("getObjectIdInRadius_for_lowerbound");
		region_l = getObjectIdInRadius(LB, queryPoint, 0, bucketnumber);
//		System.out.println(region_l.size());
		R = region_u;
//		System.out.println("removeAll");
		R.removeAll(region_l);//环内保存的是节点的id

		//提前判断环内是否为空，不存在object
		if (R.isEmpty())
			return initS;//如果环内的数据为空的话，说明不能再进行更新，就返回initS作为最优结果集

		//根据环内object到query的距离，对这些object做排序操作
//		System.out.println("sort");
		sort(R,queryPoint,0,R.size()-1);
//		o = R.get(0);



		//-------------------------------------------构造Region下界范围内的初始object-pair--------------------------------------
		//构造<o_1,o_2>的基本集
//		System.out.println("Init_obj_pair");
		obj_pair = Init_obj_pair(region_l, newTest.point[R.get(0)], LB, UB, queryPoint);//其中的参数有待调整
		if (obj_pair.isEmpty())
			return null;

		//---------------根据object-pair找到下界范围内最优的可行解，更新上下界，并且通过同样的方法找到最优解-------------------------------
		//搜索
//		System.out.println("SEARCH");
		for (int i=0; i<R.size(); i++){
			//----------------------------------------------------------SELF-ITERATIVE---------------------------------
//			System.out.println("Calcu_dist");
			dist = Calcu_dist(newTest.point[R.get(i)], queryPoint);//计算两点之间的距离

			if (dist > cost_c)
				break;
			S.add(newTest.point[R.get(i)]);

//			System.out.println("is_covered_obj_set");
			if (!is_covered_obj_set(S, queryPoint,bucketnumber)){//如果S还未包含所有的query objective
//				System.out.println("constructFeasibleSet");
				S = constructFeasibleSet(newTest.point[R.get(i)], queryPoint, bucketnumber);//通过现在确定的IRPoint构造一个可行解
				if (S.isEmpty())//如果可行解为空，那么根据R中距离最近的下一个对象来构造可行解
					continue;

				cost = comp_cost(S, queryPoint);
				if (cost < cost_c){
					initS = S;
					cost_c = cost;
					UB = cost_c;
				}
			}
			//更新<o_1,o_2>
//			System.out.println("update_obj_pair");
			update_obj_pair(obj_pair, region_l, newTest.point[R.get(i)], queryPoint, LB, UB);//更新pairwise distance owners

//			System.out.println("constructFeasibleSet_Exact");
			S = constructFeasibleSet_Exact(newTest.point[R.get(i)], queryPoint, obj_pair, bucketnumber);//根据更新过的pairwise distance owners构造精确的可行解

			if (S.isEmpty())//如果没有精确的可行解，那么根据R中距离最近的下一个对象来构造精确的可行解
				continue;

			//--------------------------------------------------------------CROSS-ITERATIVE----------------------------
			cost = comp_cost(S, queryPoint);
			if (cost < cost_c){
				initS = S;
				cost_c = cost;
				UB = cost_c;
			}

			S.clear();//释放内存
		}
//		System.out.println("END");

		return initS;
	}

	public List<Integer> getObjectIdInRadius(double radius, final IShape queryPoint, int type, ArrayList<String> bucketnumber) {
		List<Integer> po = getObjectIdInRadius(radius,queryPoint,type,bucketnumber,new NNComparator());
		return po;
	}

	/*
	* type == 0 不包括边界，用于LB < radius
	* type == 1 包括边界，用于UB <= radius
	* */
	public List<Integer> getObjectIdInRadius(double radius, final IShape queryPoint, int type, ArrayList<String> bucketnumber, final INearestNeighborComparator nnc) {
		List<IRPoint> po = new ArrayList<IRPoint>();
		List<Integer> pointidRadius = new ArrayList<>();

		try
		{
			ArrayList queue = new ArrayList();//加入NNEntry <Node/Object,distance>
			ArrayList Result_queue = new ArrayList();

			int count = 0;
			double knearest = 0.0;

			Node n = readNode(m_rootID);
			queue.add(new NNEntry(n, 0.0));//加入根节点，距离为0

			while (queue.size() != 0)
			{
				NNEntry first = (NNEntry) queue.remove(0);//取出队列中的第一个元素

				if (first.m_pEntry instanceof Node)//如果first.m_pEntry是Node的一个实例
				{
					n = (Node) first.m_pEntry;

					for (int cChild = 0; cChild < n.m_children; cChild++)
					{
						IEntry e;

						if (n.m_level == 0) {
							e = new Data(n.m_pData[cChild], n.m_pMBR[cChild], n.m_pIdentifier[cChild]);
						}else {
							e = (IEntry) readNode(n.m_pIdentifier[cChild]);//如果此节点不是叶子节点，则依次读叶子节点
						}
						if (type == 0){
							if (nnc.getMinimumDistance(queryPoint, e) >= radius)
								continue;
						}else{
							if (nnc.getMinimumDistance(queryPoint, e) > radius)
								continue;
						}

						NNEntry e2 = new NNEntry(e, nnc.getMinimumDistance(queryPoint, e));

						queue.add(e2);
					}
				}
				else
				{
					IRPoint p = newTest.point[first.m_pEntry.getIdentifier()-1];

					double dist = Calcu_dist(p, queryPoint);

					if (type == 0){
						if (dist >= radius)
							continue;
					}else{
						if (dist > radius)
							continue;
					}

					//判断关键字是否包含关键字
					for (int i=0; i<bucketnumber.size(); i++){
						if (p.bucketnumber.contains(bucketnumber.get(i))){
							pointidRadius.add(p.m_pointID);
						}
					}
				}
			}
		}
		finally
		{
			m_rwLock.read_unlock();
		}
		return pointidRadius;
	}

	private List<IRPoint> Init_obj_pair(List<Integer> region_l, IRPoint o, double lb, double ub, final IShape queryPoint) {
		double d_low, d_high, dist, d_o_q;
		IRPoint o1,o2;
		List<IRPoint> obj_pair = new ArrayList<>();

		if (region_l.size() == 0)
			return null;
		d_o_q = Calcu_dist(o,queryPoint);
		d_high = ub;

		for (int i=0; i<region_l.size()-1; i++){
			for (int j=1; j<region_l.size(); j++){
				d_low = d_o_q;
				dist = Calcu_dist(newTest.point[region_l.get(i)],newTest.point[region_l.get(j)]);

				if ((dist < d_high) && (dist > d_low)){
					if (obj_pair.size()<2){
						obj_pair.add(newTest.point[region_l.get(i)]);
						obj_pair.add(newTest.point[region_l.get(j)]);
					}else{
						obj_pair.set(0,newTest.point[region_l.get(i)]);
						obj_pair.set(1,newTest.point[region_l.get(j)]);
					}
				}
			}
		}

		return obj_pair;
	}

	private void update_obj_pair(List<IRPoint> obj_pair, List<Integer> region_l, IRPoint o, final IShape queryPoint, double lb, double ub) {
		double dist,d_low,d_high,d_o_q;

		//Compute the upper bound of d(o_1, o_2): independent on <o_1, o_2>.
		d_o_q = Calcu_dist(o,queryPoint);
		d_high = ub;

		//Remove the unsatisfied obj_pairs in the original obj_pairs set.
		if (obj_pair == null){
			System.out.println("obj_pair is empty");
			return;
		}
		for (int i=0; i<obj_pair.size(); i++){
			if (Calcu_dist(obj_pair.get(i),queryPoint) >= d_high)
				obj_pair = null;
		}

		region_l.add(o.m_pointID);

		for (int i=0; i<region_l.size(); i++){
			d_low = d_o_q;

			dist = Calcu_dist(newTest.point[region_l.get(i)],o);

			if (!(dist >= d_high || dist <= d_low)){
				obj_pair.set(0,newTest.point[region_l.get(i)]);
				obj_pair.set(1,o);
			}
		}
	}

	private List<IRPoint> constructFeasibleSet(IRPoint o, final IShape queryPoint, ArrayList<String> bucketnumber) {
		double radius;
		ArrayList<String> key = new ArrayList<>();

		IRPoint P;
		List<IRPoint> po = new ArrayList<>();
		MyVisitor2 v = new MyVisitor2();

		radius = Calcu_dist(o, queryPoint);

		po.add(o);

		//Obtain the "un-covered" keywords by S.
		key = key_exclusion(bucketnumber,o.bucketnumber);//找到o中没有包含的keywords

		for (int i=0; i<key.size(); i++){
			P = NNquery(queryPoint,key.get(i));

			if (Calcu_dist(P,queryPoint) <= radius) {
				if (P == null) {
					po = null;
					break;
				}
				po.add(P);
			}
		}

		return po;
	}

	private List<IRPoint> constructFeasibleSet_Exact(IRPoint o, final IShape queryPoint, List<IRPoint> obj_pair, ArrayList<String> bucketnumber) {
		List<IRPoint> po;
		List<IRPoint> cur_pair;
		IRPoint[] tri = new IRPoint[3];//query distance owner and pairwise distance owners

		IRPoint p = new IRPoint();
		p.m_pCoords = queryPoint.getCoordinate();

		tri[0] = o;
		tri[1] = o;
		tri[2] = p;
		po = AchievabilityCheck(tri,queryPoint,bucketnumber);
		if (po != null)
			return po;

		cur_pair = obj_pair;
		while (cur_pair != null){
			tri[1] = cur_pair.get(0);
			tri[2] = cur_pair.get(1);

			if (Calcu_dist(tri[1],tri[2]) < Math.max(Calcu_dist(tri[0],tri[1]),Calcu_dist(tri[0],tri[2])))
				continue;

			po = AchievabilityCheck(tri,queryPoint,bucketnumber);
		}

		return po;
	}

	private List<IRPoint> AchievabilityCheck(IRPoint[] tri, final IShape queryPoint, ArrayList<String> bucketnumber) {
		List<IRPoint> po = new ArrayList<>();
		List<IRPoint> O_t;
		ArrayList<String> k1,k2,k3;
		double radius_1,radius_2,radius_3,temp;
		ArrayList<String> bucket;

		//pre-checking
		k1 = key_exclusion(bucketnumber,tri[0].bucketnumber);
		k2 = key_exclusion(k1,tri[1].bucketnumber);
		k3 = key_exclusion(k2,tri[2].bucketnumber);

		bucket = k3;

		if (bucket.size() == 0){
			for (int i=0; i<tri.length; i++){
				po.add(tri[i]);
			}

			return po;
		}

		//normal-checking
		radius_1 = Calcu_dist(tri[0],queryPoint);
		radius_2 = Calcu_dist(tri[1],tri[2]);
		radius_3 = radius_2;

		if (radius_2 < radius_1){
			temp = radius_1;
			radius_1 = radius_2;
			radius_2 = temp;
		}

		O_t = getSubsetInRadius(radius_1,queryPoint,bucketnumber.size());

		obj_filter_range(O_t, tri[1], radius_2);
		obj_filter_range(O_t, tri[2], radius_3);

		if (!FeasibilityCheck(O_t,queryPoint,bucketnumber))
			return null;

		po = const_feasible_set(O_t,queryPoint,tri[1],radius_2);

		if (po == null)
			return null;

		po.add(tri[0]);
		po.add(tri[1]);
		po.add(tri[2]);

		return po;
	}

	//minimum
	private List<IRPoint> const_feasible_set(List<IRPoint> o_t, final IShape queryPoint, IRPoint irPoint, double radius_2) {
		return null;
	}

	//semantic relevance
	private boolean FeasibilityCheck(List<IRPoint> o_t, final IShape queryPoint, ArrayList<String> bucketnumber) {
		int count=0;

		for (int i=0; i<bucketnumber.size(); i++){
			for (int j=0; j<o_t.size(); j++){
				if (o_t.get(j).bucketnumber.contains(bucketnumber.get(i))){
					count++;
					break;
				}
			}
		}

		if (count == bucketnumber.size())
			return true;
		else
			return false;
	}

	/*
	* 滤除O_t中的不在radius范围内的object
	* */
	private void obj_filter_range(List<IRPoint> o_t, IRPoint o, double radius_2) {
		double dist;

		for (int i=0; i<o_t.size(); i++){
			dist = Calcu_dist(o_t.get(i),o);

			if (dist > radius_2){
				o_t.remove(i);
			}
		}
	}

	private ArrayList<String> key_exclusion(ArrayList<String> bucketnumber, ArrayList<String> bucketnumber1) {
		int tag;
		ArrayList<String> k = new ArrayList<>();

		for (int i=0; i<bucketnumber.size(); i++){
			tag = 0;
			for (int j=0; j<bucketnumber1.size(); j++){
				if (bucketnumber.get(i) == bucketnumber1.get(j)){
					tag = 1;
					break;
				}
			}
			if (tag == 0)
				k.add(bucketnumber.get(i));
		}

		return k;
	}

	private boolean is_covered_obj_set(List<IRPoint> s, final IShape queryPoint, ArrayList<String> bucketnumber) {
		int num = 0;

		for (int j=0; j<bucketnumber.size(); j++) {
			for (int i = 0; i < s.size(); i++) {
				if (s.get(i).bucketnumber.contains(bucketnumber.get(j))) {
					num++;
					break;
				}
			}
		}
		if (num == bucketnumber.size())
			return true;
		else
			return false;
	}

	public void sort(List<Integer> r, final IShape queryPoint, int start, int end) {//快速排序

		if(start >= end)
			return;

		int i=start, j=end;
		int key = r.get(start);
		double distkey = Calcu_dist(newTest.point[key], queryPoint);

		while (i<j){
			double disti = Calcu_dist(newTest.point[r.get(i)],queryPoint);
			double distj = Calcu_dist(newTest.point[r.get(j)],queryPoint);

			while (j>i && distj >= distkey){
				j--;
			}
			if (i<j){
				int temp = r.get(i);
				r.set(i,r.get(j));
				r.set(j,temp);
				i++;
			}
			while (i<j && disti <= distkey){
				i++;
			}
			if (i<j){
				int temp = r.get(j);
				r.set(j,r.get(i));
				r.set(i,temp);
				j--;
			}
		}
		sort(r,queryPoint,start,i-1);
		sort(r,queryPoint,j+1,end);
	}





	public static void sort(List<Integer> r, int start, int end) {//快速排序

		if(start >= end)
			return;

//		System.out.println("数组长度："+r.size());
		int i=start, j=end;
		int key = r.get(start);

		while (i<j){

			while (j>i && r.get(j) >= key){
				j--;
			}
			if (i<j){
				int temp = r.get(i);
				r.set(i,r.get(j));
				r.set(j,temp);
				i++;
			}
			while (i<j && r.get(i) <= key){
				i++;
			}
			if (i<j){
				int temp = r.get(j);
				r.set(j,r.get(i));
				r.set(i,temp);
				j--;
			}
		}

//		System.out.println("j的位置："+j);

//		r.set(j,key);

		sort(r,start,i-1);
		sort(r,j+1,end);
	}

	private double comp_bounds(final IShape queryPoint, ArrayList<String> bucketnumber, List<IRPoint> po) {
		double dist,lb;
		IRPoint p;
		MyVisitor2 v = new MyVisitor2();

		//计算LB
		lb = 0;
//		System.out.println("关键字的个数："+bucketnumber.size());
		for (int i=0; i<bucketnumber.size();i++){
//			System.out.println("comp_bounds.NNquery");
			p = NNquery(queryPoint,bucketnumber.get(i));

			if (p == null)
				return 0;

			dist = Calcu_dist(p,queryPoint);

			if (dist>lb)
				lb = dist;

			po.add(p);
		}
//		System.out.println("NNquery结果集长度:"+po.size());

//		//计算UB
//		cost = comp_cost(po, queryPoint);//计算出distance cost
//		ub = cost + lb;//环形区域的上界是两个最大距离的和

		return lb;
	}

	//计算cost
	double comp_cost(List<IRPoint> S, final IShape queryPoint){
//		System.out.println("对象之间的最远距离："+comp_diameter(S));
		return Math.max( comp_farthest(S, queryPoint), comp_diameter(S));
	}

	/*
	* 在这里认为S是一个可行解
	*
	* 方法是依次计算这个可行解中的object和query之间的距离，并找出最大的
	* */
	private double comp_farthest(List<IRPoint> S, final IShape queryPoint) {
		double far_dist = 0,dist;

		for (int i=0; i<S.size(); i++){
			dist = Calcu_dist(S.get(i),queryPoint);
			if (dist > far_dist)
				far_dist = dist;
		}

		return far_dist;
	}

	//计算可行解内部相互距离最大的距离
	private double comp_diameter(List<IRPoint> S) {
		double dia=0, dist;

		if (S.size() <= 1)
			return 0;

		for (int i=0; i<S.size()-1; i++){
			for (int j=1; j<S.size(); j++){
				dist = Calcu_dist(S.get(i), S.get(j));

				if (dist > dia)
					dia = dist;
			}
		}

		return dia;
	}

	//计算两个IRPoint之间的欧式距离(归一化)
	private double Calcu_dist(IRPoint irPoint, final IShape irPoint1) {
		double dist=0;
		double Coord[] = irPoint.getCoordinate();
		double Coord1[] = irPoint1.getCoordinate();

		int length = Coord.length;

		for (int i=0; i<length; i++){
			dist += Math.pow( Coord[i]-Coord1[i], 2);
		}
//		dist = 2/(1+Math.pow(Math.E,-Math.sqrt(dist)*0.01)) -1;

		return dist;
	}

//	public static void main(String[] args) throws IOException {
//
//		List<Integer> r = new ArrayList<>();
//		r.add(1);
//		r.add(5);
//		r.add(3);
//		r.add(7);
//		r.add(3);
//		r.add(2);
//
//		sort(r,0,5);
//		System.out.println(r);
//
//	}
}


class MyVisitor2 implements IVisitor {
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


