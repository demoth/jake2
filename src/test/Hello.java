/*
 * Hello.java
 * Copyright (C) 2003
 * 
 * $Id$
 */
package test;

/**
 * <code>Hello</code>
 */
public class Hello {
	
	
	public static void fuck(int x)
	{
		for (int n=2; n <= x/2; n++)
		{
			if ((x%n)==0)
			{
				System.out.print("" + n + ", ");
				x=x/n;
				n=1;
			}
		}
		System.out.println(" ->" + x);
	}
	

	public static void main(String[] args) {
		System.out.println("F*** You!");
		
		fuck(15732);
	}
}
