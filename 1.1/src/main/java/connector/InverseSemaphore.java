package connector;

public class InverseSemaphore 
{
	private static volatile int counter = -1;
	private static boolean firstThreadStart = true;
	
	public synchronized static void increment ()
	{
		if (firstThreadStart)
		{
			counter++;
			firstThreadStart = false;
		}
		counter++;
		
	}
	
	public synchronized static void decrement ()
	{
		counter--;
	}
	
	public static int getCounter()
	{
		return counter;
	}
}
