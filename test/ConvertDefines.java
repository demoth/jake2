import java.io.*;
import java.util.StringTokenizer;


/** This class converts the #define statements into java 
 * public static final int statements.
 *  
 * Additionally it converts some mframe_t statements, when the filename 
 * starts with "jake2/game/M_" . 
 */  

public class ConvertDefines
{
	public static String convertDefine(String in)
	{
		StringBuffer out= new StringBuffer();

		StringTokenizer tk= new StringTokenizer(in);
		while (tk.hasMoreElements())
		{
			String token= tk.nextToken();

			// finds the define
			if (token.equals("#define"))
			{
				out.append("	public final static int ");
				out.append(tk.nextToken());
				out.append("= ");
				out.append(tk.nextToken());
				out.append(";\t");

				// append rest and out.
				while (tk.hasMoreElements())
				{
					out.append(tk.nextToken());
					out.append(" ");
				}
			}
			else
			{
				out.append(token);
				out.append(" ");
			}
		}
		return out.toString();
	}

	/********************************************/
	public static void main(String args[])
	{
		try
		{
			
			System.out.println("\n".trim().length());
			String line;
			String filename;
			boolean m_doc = true;

			if (args.length == 0)
			{
				filename= "jake2/Defines.java";
			}
			else
				filename= args[0];
				
			if (filename.startsWith("jake2/game/M_"))
				m_doc = true;
			else m_doc = false;

			FileWriter fw= new FileWriter(filename + ".new");
			FileReader fr= new FileReader(filename);
			BufferedReader br= new BufferedReader(fr);

			while (br.ready())
			{
				line= br.readLine();
				if (line.indexOf("#define") != -1)
					fw.write(convertDefine(line) + "\n");		
					
							
				else if (m_doc && line.trim().startsWith("mframe_t ") && line.indexOf("new") == -1)
				{
					fw.write(" static " + line + " new mframe_t[] \n");
					while (br.ready())
					{
						line= br.readLine();
						// opening brace

						if (line.indexOf("{")!=-1)
							fw.write(line + "\n");
						// opening brace
						else if (line.indexOf("}")!=-1)
						{						
							fw.write(line + "\n");
							break;
						
						}
						else if (line.trim().length()==0)
							fw.write("\n");
						else
						{
							String comma ="";
							String line1 = line;
							
							if (line.endsWith(","))
							{
								line1=line.substring(0,line1.length()-1);
								comma = ",";
							}
							fw.write("\tnew mframe_t (" + line1 + ")" + comma + "\n");
						}
					}
				}
				else if (m_doc && line.trim().startsWith("mmove_t"))
				{
					int pos1 = line.indexOf("{");
					int pos2 = line.indexOf("}");
					String seg1 = line.substring(0,pos1);
					String seg2 = line.substring(pos1+1, pos2);
					String seg3 = line.substring(pos2+1, line.length());
					fw.write("static " + seg1 + " new mmove_t (" + seg2 + ")" + seg3 + "\n\n");
					//fw.write(line);
				}
				else
					fw.write(line + "\n");
			}
			fr.close();
			fw.close();

			//System.out.println(convertDefine("#define	IT_WEAPON		1		// use makes active weapon"));
			//System.out.println(convertDefine("#define	IT_AMMO			2"));
		}
		catch (Exception e)
		{
			System.err.println("Exception:" + e);
		}
	}
}
