package com.riintouge.armoredattire;

public class EnumUtil
{
	public static < T extends Enum< T > > T valueOfOrDefault( Class< T > clazz , String name , T def )
	{
		try
		{
			return Enum.valueOf( clazz , name );
		}
		catch( IllegalArgumentException e )
		{
			return def;
		}
	}
}
