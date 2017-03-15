package Test;

import spatialindex.spatialindex.IShape;
import spatialindex.spatialindex.Point;

import java.util.ArrayList;
import java.util.Arrays;

public class IRPoint extends Point implements IShape, Cloneable{

	public int m_pointID;
	Point point = new Point();
	public float[] topics = new float[newTest.dimention];

	//public double[] m_pCoordinate = new double[2];  //每个点的坐标
	//public String Keyword;
	//public double distance;

	public ArrayList<String> bucketnumber = new ArrayList<String>();//用来存储这个空间文本对象对应的桶号

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		if (!super.equals(o)) return false;

		IRPoint point1 = (IRPoint) o;

		if (m_pointID != point1.m_pointID) return false;
		if (point != null ? !point.equals(point1.point) : point1.point != null) return false;
		if (!Arrays.equals(topics, point1.topics)) return false;
		return bucketnumber != null ? bucketnumber.equals(point1.bucketnumber) : point1.bucketnumber == null;

	}

	@Override
	public int hashCode() {
		int result = m_pointID;
		result = 31 * result + (point != null ? point.hashCode() : 0);
		result = 31 * result + Arrays.hashCode(topics);
		result = 31 * result + (bucketnumber != null ? bucketnumber.hashCode() : 0);
		return result;
	}

	@Override
	public String toString() {
		return "IRPoint{" +
				"m_pointID=" + m_pointID +
				", point=" + point +
				", topics=" + Arrays.toString(topics) +
				", bucketnumber=" + bucketnumber +
				'}';
	}

	//
//	@Override
//	public boolean equals(Object o) {
//		if (this == o) return true;
//		if (o == null || getClass() != o.getClass()) return false;
//		if (!super.equals(o)) return false;
//
//		IRPoint point1 = (IRPoint) o;
//
//		if (m_pointID != point1.m_pointID) return false;
//		if (point != null ? !point.equals(point1.point) : point1.point != null) return false;
//		if (!Arrays.equals(topics, point1.topics)) return false;
//		return bucketnumber != null ? bucketnumber.equals(point1.bucketnumber) : point1.bucketnumber == null;
//
//	}
//
//	@Override
//	public int hashCode() {
//		int result = dimention;
//		result = 31 * result + m_pointID;
//		result = 31 * result + (point != null ? point.hashCode() : 0);
//		result = 31 * result + Arrays.hashCode(topics);
//		result = 31 * result + (bucketnumber != null ? bucketnumber.hashCode() : 0);
//		return result;
//	}
//
//	@Override
//	public String toString() {
//		return "IRPoint{" +
//				"dimention=" + dimention +
//				", m_pointID=" + m_pointID +
//				", point=" + point +
//				", topics=" + Arrays.toString(topics) +
//				", bucketnumber=" + bucketnumber +
//				'}';
//	}
}
