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
// License along with this library; if not, write to the Free Software
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

package spatialindex.storagemanager;

import java.util.*;

public class PropertySet
{
	private HashMap m_propertySet = new HashMap();

	public Object getProperty(String property)  //property涓洪敭鍊�
	{
		return m_propertySet.get(property);
		//get鏂规硶锛氳繑鍥炴寚瀹氶敭鎵�鏄犲皠鐨勫�硷紝濡傛灉璇ラ敭涓嶅寘鍚换浣曟槧灏勫叧绯诲垯杩斿洖null
	}

	public void setProperty(String property, Object o)
	{
		m_propertySet.put(property, o);   //key = property; value = o;
		//put鏂规硶锛氬湪姝ゆ槧灏勪腑鍏宠仈鎸囧畾鍊煎拰鎸囧畾閿�傚鏋滆鏄犲皠浠ュ墠鍖呭惈浜嗕竴涓閿殑鏄犲皠鍏崇郴锛屽垯鏃у�艰鏇挎崲
	}
} // PropertySet
