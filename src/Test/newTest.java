package Test;

import spatialindex.rtree.RTree;
import spatialindex.spatialindex.*;
import spatialindex.storagemanager.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class newTest {

	/*
	* 查询对象的个数
	* query objective的个数
	* 哈希表的个数
	* 哈希函数的个数
	* */
	public final static int sumPoints = 140000;
	public static IRPoint[] point = new IRPoint[sumPoints];//所有的点的数据

	static int objective_number = 4;
	private IRPoint[] querypoint = new IRPoint[objective_number];

	private int countPoint = 0;//点计数
	private int querycount = 0;//查询点关键字个数计数

	public static int dimention = 20;//topic dimension
	private static int L = 3;  //hash表的数量,L
	private static int M = 9;  //每个hash表对应的hash函数的数量, M

	double beta = 0.5;
	double alpha = 0.5;

	//------------------------------------------------------lsh参数设置----------------------------------------------------------------------------
	private ArrayList<ArrayList<double[]>> a = new ArrayList<ArrayList<double[]>>();  //LSH的a
	private double b;   //LSH的b，为0~w之间的随机数
	private ArrayList<HashMap<String,ArrayList<IRPoint>>> lshIndex = new ArrayList<HashMap<String,ArrayList<IRPoint>>>(); 
	private static double w = 0.5;   //直线上分段的段长，即LSH的w
	public static HashMap<Integer,IRPoint> map = new HashMap<Integer,IRPoint>();
	
	public static HashMap<Integer,HashMap<String,ArrayList> > inverted = new HashMap<>();

	public static double[] coord = new double[2];//用于存储查询点的坐标
	
	//===============================================================================


	public static void main(String[] args) throws IOException
	{
		String QueryFile = "D:\\LSHdata\\queryset\\query" + objective_number + "_" + dimention + "_NYC.txt";
		String Dataset = "D:\\LSHdata\\dataset\\objects_NYC_" + dimention + "Topics.txt";

		String method = "DiameterMoSKQ";//Top-K MoSKQ DiameterMoSKQ IMOSKQ
		newTest irtree = new newTest(/*(i%6+1)*2*/);
		if(method.equals("Top-K")){
			irtree.ReadAll(Dataset);
		}else{
			irtree.CreateLSH(L,M,w,Dataset);
		}
		irtree.query(QueryFile,"D:\\LSHdata\\rtreedata.dat",method);

		System.out.println("查询结束！");
	}

	//------------------------------------createlsh---------------------------------------------------------------------------
	public void CreateLSH(int L, int M, double w, String filename){
		
		ReadAll(filename);
//		System.out.println("read points successfully!");
		
		setVarA(L,M);
//		System.out.println("set properties successfully!");
		
		Random ran = new Random(); //随机数b
	    b = ran.nextDouble()*w;
		
        //---------------------LSH的初始化-----------------------
	    lshIndex = new ArrayList<HashMap<String,ArrayList<IRPoint>>>();
        
        for(int l = 0; l < L; l++) {
        	lshIndex.add(new HashMap<String,ArrayList<IRPoint>>());
		}

		for(int tmpnumber = 0;tmpnumber <countPoint;tmpnumber++) {//直接放置到lshIndex中
			putLSH(point[tmpnumber],L,M,w);
		}
//		System.out.println("LSH初始化成功！");
	}
	
	public void ReadAll(String filename)//读出多个文件当中的数据，每一个文件当中保存了一个keyword的topic分布
	{
		LineNumberReader lr = null;
		try {
			lr = new LineNumberReader(new FileReader(filename));
		} catch (FileNotFoundException e) {
			System.err.println("Cannot open trajectory file " + filename + ".");
			System.exit(-1);
		}

		try {
			String line = lr.readLine();
			
			while((line != null) && (countPoint<sumPoints)) {
				String[] temp = line.split("\\s+");

				point[countPoint] = new IRPoint();
				
				point[countPoint].m_pointID = Integer.parseInt(temp[0]); //读入对象编号
				point[countPoint].m_pCoords[0] = Double.parseDouble(temp[1]);  //读入轨迹点的横坐标
				point[countPoint].m_pCoords[1] = Double.parseDouble(temp[2]);  //读入轨迹点的纵坐标
				for(int i=0; i<dimention; i++)
				{
						point[countPoint].topics[i] = Float.parseFloat(temp[i+3]);
				}

				newTest.map.put(point[countPoint].m_pointID, point[countPoint]);
				
				countPoint++;
				line = lr.readLine();
			}

		} catch (IOException e) {
			e.printStackTrace();
		}	
	}

	private void setVarA(int L, int M) {
		Random normalRandom = new Random();
		double tmp = 0;
					
		for(int l = 0; l < L; l++) {
			a.add(new ArrayList<double[]>());
			
			for(int m = 0; m < M; m++) {//M个hash函数	
				a.get(l).add(new double[dimention]);//Config.dimention最终等于topic的数量，所以应该是一个function中的桶的个数
				//a.get(l)代表了hash table中的hash function

				for(int k = 0; k < dimention; k++) {//维度 bucket
								
					tmp = normalRandom.nextGaussian();//获取下一个伪高斯的double值
					while(tmp < 0) {//并保证这个随机数为正
						tmp = normalRandom.nextGaussian();   
					}
					a.get(l).get(m)[k] = tmp;//为特定位置的桶内设置随机数
				}
			}
		}
//		System.out.println("a:");
//		for(int i=0;i<a.size();++i){
//			for(int j=0;j<a.get(i).size();++j){
//				for(int k=0;k<a.get(i).get(j).length;++k)
//				System.out.println(a.get(i).get(j)[k]);
//			}
//		}
//		System.exit(0);
	}
	
	private void putLSH(IRPoint tmp, int L, int M, double W) {
//		ArrayList<Integer> bucketnumber = new ArrayList<Integer>();
//        System.out.println("tmp topics are:" + tmp.topics);
//        System.out.println("tmp ID are:" + tmp.m_pointID);
//        System.out.println();
		String key = "";

        for(int i = 0; i < L; i++) {
        	
			double b = Math.random();
			
			key = getKey(tmp.topics,i,a,b,M,W);
//			System.out.println("key" + key);
			
			tmp.bucketnumber.add(key);//在每一个hash table中都获取一个桶号，并加入到空间文本对象当中
				//值得注意的是在这里桶号是String类型的
			
			if(lshIndex.get(i).get(key) == null) {//如果hash表中不存在key对应的项，则增加key，value的项
				ArrayList<IRPoint> tmpArr = new ArrayList<IRPoint>();
				tmpArr.add(tmp);
				lshIndex.get(i).put(key, tmpArr);
			}
			else {//如果hash表中存在hash存在key对应的项，则直接加入轨迹点
				lshIndex.get(i).get(key).add(tmp);
			}
		}
	}

	private String getKey(float[] topics, int L, ArrayList<ArrayList<double[]>> a, double b, int M, double W) {

		String result = "";//储存第l个hash table的M个hash functions对应的M个hash结果
		
		for(int i = 0; i < M; i++) {
			int hashResult = hashFamily(topics,a.get(L).get(i),b,W,M);//a.get(l).get(i)表示在第l个hash table中的第i个桶里
			result += hashResult;
		}
		return result; //返回hash结果
		
	}

	private int hashFamily(float[] topics, double[] a, double b, double W, int M) { 
//		System.out.println("topics:");
//		for(int i=0; i< topics.length; ++i){
//			System.out.print(topics[i]);
//		}
//		System.out.println();
//		
		int h = 0;
		double tmp = b;
		
		for(int i =  0; i < dimention; i++) {
			tmp += topics[i]*a[i];
		}
		tmp = tmp/W;
		h = (int)tmp;
//		System.out.println("h"+h);
//		System.exit(0);
		return h;
	}
	
	private double guiyi(double x){
		return 2.0/(1+Math.pow(Math.E, -x)) -1;
	}
	
	private double ed(Point queryPoint,IRPoint o){//欧式距离
		double d =0;
		for(int i = 0;i<o.m_pCoords.length;++i){
//			System.out.println("queryPoint:"+queryPoint.getCoord(i));
//			System.out.println("o:"+o.m_pCoordinate[i]);
			d+= Math.pow(queryPoint.getCoord(i)-o.m_pCoords[i], 2);
		}
		
		d = Math.sqrt(d);
		return d;
		
	}
	
	
	private List<IRPoint> allPointsInRadius(Point queryPoint2,int radius){
		List<IRPoint> l = new ArrayList<IRPoint>();
		for(int i = 0;i<point.length;++i){
			if(guiyi(ed(queryPoint2,point[i]))<=guiyi(radius)){
				l.add(point[i]);
			}
		}
		return l;
		
	}
	
	
	private List<List<IRPoint>> allCombines(List<IRPoint> allPointsInRadius,int queryCount, Point query, double alpha,double beta){
		List<List<IRPoint>> l = new ArrayList<List<IRPoint>>();
		for( int i =1;i<=queryCount;++i){
			l.addAll(allCombinesWithK(allPointsInRadius,i,query,alpha,beta));
		}
		return l;
		
	}
	
	private Collection<? extends List<IRPoint>> allCombinesWithK(
			List<IRPoint> allPointsInRadius, int k, Point query, double alpha,double beta) {
		List<List<IRPoint>> all = new ArrayList<List<IRPoint>>();
		permutationIRPoint(allPointsInRadius,all,0,new  ArrayList<IRPoint>(),k,query,alpha,beta);
		return all;
	}
	
	private void permutationIRPoint(List<IRPoint> inputList, List<List<IRPoint>> resList, int ind, ArrayList<IRPoint> arr,int k, Point query, double alpha,double beta) {
		 if(k == 0){
//			   resList.add((ArrayList<IRPoint>) arr.clone());
			 	double ds = ds(query,arr,alpha,beta);
//			 	System.out.println(ds);
			 	if(ds<cost){
			 		cost = ds;
			 		System.out.println(ds);
			 	}
			   return;
		   }
	   if(ind >= inputList.size())
		   return;
	  
	   for(int i = ind;i<inputList.size();++i){
		   arr.add(inputList.get(i));
		   permutationIRPoint(inputList,resList,i+1,arr,k-1,query,alpha,beta);
		   arr.remove(arr.size()-1);
	   }
	   
	}
	
	private Collection<? extends List<IRPoint>> allCombinesWithK2(
			List<IRPoint> allPointsInRadius, int k, Point query, double alpha,double beta) {
		List<List<IRPoint>> all = new ArrayList<List<IRPoint>>();
		permutationIRPoint2(allPointsInRadius,all,0,new  ArrayList<IRPoint>(),k,query,alpha,beta);
		return all;
	}
	
	private void permutationIRPoint2(List<IRPoint> inputList, List<List<IRPoint>> resList, int ind, ArrayList<IRPoint> arr,int k, Point query, double alpha,double beta ) {
		 if(k == 0){
//			   resList.add((ArrayList<IRPoint>) arr.clone());
			 	double ds = ds2(query,arr,alpha,beta);
//			 	System.out.println(ds);
			 	if(ds<cost)
			 		cost = ds;
			   return;
		   }
	   if(ind >= inputList.size())
		   return;
	  
	   for(int i = ind;i<inputList.size();++i){
		   arr.add(inputList.get(ind));
		   permutationIRPoint2(inputList,resList,i+1,arr,k-1,query,alpha,beta);
		   arr.remove(arr.size()-1);
	   }
	   
	}
	
	
	private double ds(Point query,List<IRPoint> points,double alpha,double beta){
		double d = 0;
		
		double maxA=0;
		double maxB=0;
//		System.out.println("size:"+points.size());
		
		for(int i = 0;i<points.size();++i){ // 求query到point的最大距离
			double d1 = guiyi(ed(query,points.get(i)));
			if(d1>maxA)
				maxA = d1;
		}
		
		//point之间的最大距离
		for(int i = 0;i<points.size();++i){
			for(int j = i+1;j<points.size();++j){
				double d1 = guiyi(ed(points.get(i),points.get(j)));
				
				if(d1>maxB)
					maxB = d1;
			}
		}
		
//		System.out.println(maxA+","+maxB);
		
		return beta*(alpha*maxA+(1-alpha)*maxB) + (1-beta)*semantics(query, points);
		
	}
	
	private double ds2(Point query,List<IRPoint> points,double alpha,double beta){
		double d = 0;
		
		double maxA=0;
		double maxB=0;
//		System.out.println("size:"+points.size());
		
		for(int i = 0;i<points.size();++i){ // 求query到point的最大距离
			double d1 = guiyi(ed(query,points.get(i)));
			if(d1>maxA)
				maxA = d1;
		}
		
		//point之间的最大距离
		for(int i = 0;i<points.size();++i){
			for(int j = i+1;j<points.size();++j){
				double d1 = guiyi(ed(points.get(i),points.get(j)));
				
				if(d1>maxB)
					maxB = d1;
			}
		}
		
//		System.out.println(maxA+","+maxB);
		
		return beta*(alpha*maxA+(1-alpha)*maxB);
		
	}
	
	private double semantics(Point query,List<IRPoint> points){
		double d = 0;
		for(int i=0;i<querypoint.length;++i){
			double d1 = Double.MAX_VALUE;
			float[] f = querypoint[i].topics;
			for(int j=0;j<points.size();++j){
				double d2 = 0;
				for(int k =0;k<f.length;k++){
					d2+=(f[k]-points.get(j).topics[k])*(f[k]-points.get(j).topics[k]);
				}
				d2 = Math.sqrt(d2);
				d2 = guiyi(d2);
				if(d2<d1)
					d1 = d2;
			}
			d+=d1;
		}
		return d;
		
	}

	private double ed(IRPoint irPoint, IRPoint o) {
		double d =0;
		for(int i = 0;i<o.m_pCoords.length;++i){
			d+= Math.pow(irPoint.m_pCoords[i]-o.m_pCoords[i], 2);
		}
		
		d = Math.sqrt(d);
		return d;
	}



	private List<IRPoint> minCostCombine(Point query,List<IRPoint> allPointsInRadius,int queryCount,double alpha,double beta){
		List<List<IRPoint>> allCombines = allCombines(allPointsInRadius, querycount,query,alpha,beta);
		List<IRPoint> r = new ArrayList<IRPoint>();
//		double min = Double.MAX_VALUE;
//		int index = 0;
//		for(int i = 0;i<allCombines.size();++i){
//			double ds = ds(query,allCombines.get(i),alpha);
////			System.out.println(ds);
//			if(ds < min){
//				min = ds;
//				index = i;
//			}
//		}
////		System.out.println(min);
//		r.addAll(allCombines.get(index));
		return r;
	}
	
	//------------------------------------------------------------------------------------------------------------------------
	
	private double cost = Double.MAX_VALUE;
	private List<IRPoint> po = new ArrayList<IRPoint>();

	public void query(String queryfile,String rtreefile,String querytype) {
		
		//--------------------------------search-----------------------------------------
		BufferedReader lr = null;
		try {
			lr = new BufferedReader(new FileReader(queryfile));
		} catch (FileNotFoundException e) {
			System.err.println("Cannot open file " + queryfile + ".");
			System.exit(-1);
		}
		
		int indexIO = 0;
		int leafIO = 0;
		
		double[] qf1 = new double[2];
		
		String line = null;
		try {
			line = lr.readLine();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		while(line != null)
		{
			float[] topic = new float[dimention];
			float temp = 0;
			int countnumber = 0;
			int number = 3;
			
			String[] querytemp2 = line.split(" ");
			
			querypoint[querycount] = new IRPoint();
			
			//获取横纵坐标
//            querypoint[0].m_pCoords = new double[2];
            querypoint[0].m_pCoords[0] = Double.parseDouble(querytemp2[0]);
            querypoint[0].m_pCoords[1] = Double.parseDouble(querytemp2[1]);
    		qf1[0] = Double.parseDouble(querytemp2[0]);
    		qf1[1] = Double.parseDouble(querytemp2[1]);

    		Random r = new Random();
    		int nextInt = r.nextInt(point.length);
    		qf1[0] = point[nextInt].m_pCoords[0] + r.nextInt(20) - 20;
    		qf1[1] = point[nextInt].m_pCoords[1] + r.nextInt(20) - 20;
    		newTest.coord[0] = querypoint[querycount].m_pCoords[0];
    		newTest.coord[1] = querypoint[querycount].m_pCoords[1];
			
    		//获取topics的值
			while(countnumber < dimention) {
				temp = Float.parseFloat(querytemp2[number++]);//获取关键字列表
				topic[countnumber++] = temp;
			}
			querypoint[querycount].topics = topic;
			
			querycount++;
			
			try {
				line = lr.readLine();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		Point queryPoint = new Point(qf1);

		if(querytype.equals("Top-K")){
			System.out.println("in topk");
			long start = System.currentTimeMillis();

			List<IRPoint> po = new ArrayList<IRPoint>();
			List<IRPoint> optimalpo = new ArrayList<IRPoint>();
			int radius = 0;

			List<IRPoint> minCostCombine = null;
			double last = 0;
			while(cost >= alpha*beta*guiyi(radius) )
			{
				radius += 5;
				List<IRPoint> allPointsInRadius = allPointsInRadius(queryPoint, radius);
				minCostCombine = minCostCombine(queryPoint,allPointsInRadius,querycount,alpha,beta);
				System.out.println("min:"+cost);
				if(cost == last){
					break;
				}
				last = cost;
				
			}
			
			System.out.println(cost);
			System.out.println("time:"+(System.currentTimeMillis()-start)/1000.0);
			
		}else{
			try
            {
                PropertySet ps1 = new PropertySet();

                Boolean b2 = new Boolean(true);
                ps1.setProperty("Overwrite", b2);

                ps1.setProperty("FileName", rtreefile);

                Integer i = new Integer(4096);
                ps1.setProperty("PageSize", i);
                IStorageManager diskfile = new DiskStorageManager(ps1);
                IBuffer file = new RandomEvictionsBuffer(diskfile, 10, false);

                PropertySet ps2 = new PropertySet();

                Double f = new Double(0.7);
                ps2.setProperty("FillFactor", f);

                i = new Integer(100);
                ps2.setProperty("IndexCapacity", i);   //capacity：容量
                ps2.setProperty("LeafCapacity", i);

                i = new Integer(2);
                ps2.setProperty("Dimension", i);

                //---------------------------Create IRtree Index------------------------------

                ISpatialIndex tree = new RTree(ps2, file);

//    			System.out.println("Build RTree Successfully!");

                int id;
                String ss;

                //point的坐标
                double[] d = new double[2];

                for(int j = 0;j < countPoint;j++)
                {
//    				System.out.println("j的值为："+j);
                    id = point[j].m_pointID;

                    d[0] = point[j].m_pCoords[0];//x
                    d[1] = point[j].m_pCoords[1];//y
                    Point p = new Point(d);

                    ArrayList<String> ids = point[j].bucketnumber;
                    for(int k = 0;k<ids.size();++k){
                        tree.insertData(ids.get(k).getBytes(), p, id);//
                    }
                }

                Integer indexID = (Integer) ps2.getProperty("IndexIdentifier");
                //System.err.println("Index ID: " + indexID);
                boolean ret = tree.isIndexValid();
                if (ret == false) System.err.println("Structure is INVALID!");
				tree.flush();

//				System.out.println("建立倒排文件");
				tree.BuildInvertedList();
    			System.out.println("创建IR树成功!");

				//-------------------------------------------------------------------------------

                //---------------------------获取query的桶号------------------------------------
                ArrayList<ArrayList<String>> querytemp = new ArrayList<ArrayList<String>>();

                for(int m = 0; m < querypoint.length-1; m++){//获取query中的每一组topic的桶号
                    ArrayList<String> keytemp = new ArrayList<String>();
                    String key = "";
                    for(int n = 0; n<L; n++){//在每个hash table中获取一个桶号
                        key = getKey(querypoint[m].topics, n, a, b, M, w);
                        keytemp.add(key);
                    }
                    querytemp.add(keytemp);
                }

                //找出所有查询的桶号的集合
                ArrayList<ArrayList<String>> resultlist = new ArrayList<ArrayList<String>>();

                resultlist = permutation(querytemp);
    			System.out.println("数据处理完成，进入查询函数MoSKQ!");
                //---------------------------------------------------------------------------
                long start = System.currentTimeMillis();//开始计时
                MyVisitor vis = new MyVisitor();

                if (querytype.equals("MoSKQ")) {
					System.out.println("进入MoSKQ查询！");
					po = tree.CollectiveQuery_temp(queryPoint, resultlist,vis);
					System.out.println("打印MoSKQ结果：");
					printPoi(po);
                }
                else if (querytype.equals("DiameterMoSKQ")){
					System.out.println("进入DiameterMoSKQ查询！");
					po = tree.DiameterMoSKQ(queryPoint, resultlist);
					System.out.println("打印DiameterMoSKQ结果：");
					printPoi(po);
                }
				else if(querytype.equals("IMOSKQ")) {
					int N = 3;

					Random r =new Random();
					ArrayList<ArrayList<String>> resultlisttemp = new ArrayList<ArrayList<String>>();

					for(int c=0; c<N; c++) {
						resultlisttemp.add(resultlist.get(r.nextInt(resultlist.size())));
					}

					po = tree.CollectiveQuery_temp(queryPoint, resultlisttemp,vis);
				}
                else {
                    System.err.println("Unknown query type.");
                    System.exit(-1);
                }

                indexIO += vis.m_indexIO;
                leafIO += vis.m_leafIO;
                    // example of the Visitor pattern usage, for calculating how many nodes
                    // were visited.
                System.out.println();
                line = lr.readLine();

                //---------------------------------------------------------------------------------------------
                long end = System.currentTimeMillis();
                Thread.sleep(2);
                System.err.println("Seconds: " + ((end - start) / 1000.0f));

                diskfile.close();

            }catch(Exception e)
            {
                e.printStackTrace();
            }
		}
	}

    private void printPoi(List<IRPoint> po){
        System.out.println("po size:"+po.size());
        for(int i = 0;i < po.size(); ++i){
            System.out.println(i + " :"+po.get(i));
        }
    }
	
	private void printPo(List<List<IRPoint>> po){
		System.out.println("po size:"+po.size());
		for(int i = 0;i < po.size(); ++i){
			System.out.println(i + " size:"+po.get(i).size());
		}
	}
	
	private ArrayList<ArrayList<IRPoint>> permutationIRPoint(Point query,double alpha,double beta,List<List<IRPoint>> po){
		
		ArrayList<ArrayList<IRPoint>> resList = new ArrayList<ArrayList<IRPoint>>();
		ArrayList<IRPoint> arr = new ArrayList<IRPoint>();
		permutationIRPoint(query,alpha,beta,po, resList, 0, arr);//new char[inputList.size()] -> new ArrayList<String>() 
	    return resList;
	}

	private void permutationIRPoint(Point query,double alpha,double beta,List<List<IRPoint>> po, ArrayList<ArrayList<IRPoint>> resList, int ind, ArrayList<IRPoint> arr) {
	    if(ind == po.size()){
	    	double ds = ds2(query,arr,alpha,beta);
//		 	System.out.println(ds);
		 	if(ds<cost){
		 		cost = ds;
		 		this.po.clear();
		 		this.po.addAll(arr);
		 	}
//	        Object clone = arr.clone();
//			resList.add((ArrayList<IRPoint>) clone);
	        return;
	    }

	    for(IRPoint c: po.get(ind)){
	        arr.add(c);
	        permutationIRPoint(query,alpha,beta,po, resList, ind + 1, arr);
	        arr.remove(arr.size()-1);
	    }
	}

//	private ArrayList<String> GetOptimalCBS(ArrayList<ArrayList<String>> querytemp) {
//		ArrayList<String> ocbs = new ArrayList<String>();
//		ArrayList<String> tempocbs = new ArrayList<String>();
//		List<IRPoint> po = new ArrayList<IRPoint>();
//				
//		for(int i=0; i<querytemp.size(); ++i){
//			Random x = new Random();
//			
//			int temp = x.nextInt(3)+1;
//			
//			tempocbs.add(querytemp.get(i).get(temp));
//		}
//		
//		po = CollectiveQuery_temp(p, tempocbs, vis);
//		
//		return ocbs;
//	}

	public int getQuerycount() {
		return querycount;
	}

	public void setQuerycount(int querycount) {
		this.querycount = querycount;
	}

	private ArrayList<String> Replace(ArrayList<String> tempocbs,ArrayList<ArrayList<String>> querytemp) {
		Random x = new Random();
		ArrayList<String> tempocbs2 = new ArrayList<String>();
		
		int temp = x.nextInt(2);
		int temp2 = x.nextInt(3);
		
		tempocbs.set(temp, querytemp.get(temp).get(temp2));
		
		return tempocbs;
	}

	private ArrayList<ArrayList<IRPoint>> SubSet(List<IRPoint> po, int k) {

		ArrayList<ArrayList<IRPoint>> allset = new ArrayList<>();
		
		for(int i=1; i<=k; ++i){
			ArrayList<ArrayList<IRPoint>> resList = new ArrayList<>();
			resList = perm(po, k);
			allset.addAll(resList);
		}
		
		return allset;
	}

	private ArrayList<ArrayList<String>> permutation(ArrayList<ArrayList<String>> inputList){
		ArrayList<ArrayList<String>> resList = new ArrayList<ArrayList<String>>();
		ArrayList<String> arr = new ArrayList<String>();
		for(int i=0; i<inputList.size(); i++){
			arr.add("");
		}
	    permutationInt(inputList, resList, 0, arr);//new char[inputList.size()] -> new ArrayList<String>() 
	    return resList;
	}

	private void permutationInt(ArrayList<ArrayList<String>> inputList, ArrayList<ArrayList<String>> resList, int ind, ArrayList<String> arr) {
	    if(ind == inputList.size()){
	        Object clone = arr.clone();
			resList.add((ArrayList<String>) clone);
	        return;
	    }

	    for(String c: inputList.get(ind)){
	        arr.set(ind, c);
	        permutationInt(inputList, resList, ind + 1, arr);
	    }
	}
	
	private static ArrayList<ArrayList<IRPoint>> perm(List<IRPoint> inputList,int k ){
		ArrayList<ArrayList<IRPoint>> resList = new ArrayList<>();
		
	    ArrayList<IRPoint> arr = new ArrayList<>();
		permInt(inputList, resList, 0,0,arr,k );//new char[inputList.size()] -> new ArrayList<String>() 
	    return resList;
	}

	private static void permInt(List<IRPoint> inputList, List<ArrayList<IRPoint>> resList, int ind, int gnd, ArrayList<IRPoint> arr,int k) {
	    if(ind == k){
	    	ArrayList<IRPoint> clone = (ArrayList<IRPoint>) arr.clone();
			resList.add(clone);
	        return;
	    }
	    for(int i = gnd;i<inputList.size();++i){
	    	arr.add(inputList.get(i));
	    	permInt(inputList,resList,ind+1,i+1,arr,k);
	    	arr.remove(arr.size()-1);
	    }
	}
}

//public class MyVisitor implements IVisitor {
//	public int m_indexIO = 0;
//  	public int m_leafIO = 0;
//
//  	public void visitNode(final INode n)
//  	{
//  		if (n.isLeaf()) m_leafIO++;
//  		else m_indexIO++;
//  	}
//
//  	public String visitData(final IData d)
//  	{
//  		String Keyword = new String(d.getData());
//  		//System.out.println("结果点的id为：" + d.getIdentifier());
//  			// the ID of this data entry is an answer to the query. I will just print it to stdout.
//  		return Keyword;
//  	}
// }
