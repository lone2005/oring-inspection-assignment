/*
 * Institute of Technology, Blanchardstown
 * Computer Vision (Year 4)
 * O-Ring Image Inspection Assignment
 * Main Class
 * Author: Dan Flynn
 */

package main.java;

import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferByte;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

import javax.imageio.ImageIO;
import javax.swing.*;

import main.java.ccl.CCL;
import org.opencv.core.*;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;

public class ORingInspection {

    public static void main(String[] args) {

        //Load native OpenCV library
        System.loadLibrary(Core.NATIVE_LIBRARY_NAME);

        //Create and set up the window.
        JFrame window = new JFrame("OpenCV O-Ring Inspection");
        window.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);

        //Setup the JLabel image container
        JLabel imgContainer = new JLabel();
        window.getContentPane().add(imgContainer, BorderLayout.CENTER);

        //Display the window.
        window.pack();
        window.setVisible(true);

        //Begin image processing preparation
        Mat imgInput = new Mat();
        Mat imgOutput = new Mat();
        //CV_8UC3 = 8-bit unsigned integer matrix/image with 3 channels
        Mat histImg = new Mat(256,256, CvType.CV_8UC3); //TODO: Integrate into JFrame

        //Loop variables
        int i=1; //Image counter

        while(true) {

            ///READ IMAGE///
            File file = null;
            try {
                //Get next image file
                file = new File(ORingInspection.class.getResource("/oring-images/Oring" + i + ".jpg").toURI());
                //Load Greyscale image with OpenCV
                imgInput = Highgui.imread(file.getPath(),0);
            }
            catch (URISyntaxException ignored) {}
            catch (NullPointerException ignored) {break;}

            i++; //Advance to next


            ///PROCESS IMAGE///
            //1. Threshold the image using calculated histogram
            int [] h = hist(imgInput);
            int t = calculateOtsu(imgInput, h);
            threshold(imgInput, t);

            //2. Draw histogram for JFrame
            //drawHistogram(histImg, h); //TODO: Integrate into JFrame

            //3. Close any small holes in the rings
            dilate(imgInput);
            erode(imgInput);

            //4. Perform CCL to remove any spurious artifacts
            performCCL(imgInput, file);

            //5. Analyse regions to classify the Oring (Pass/Fail)

            //6. Measure the image processing time (text annotation)


            ///DISPLAY IMAGE///
            Imgproc.cvtColor(imgInput, imgOutput, Imgproc.COLOR_GRAY2BGR);

            //Convert to a Java BufferedImage so we can display in a label
            BufferedImage javaImg = Mat2BufferedImage(imgOutput);
            imgContainer.setIcon(new ImageIcon(javaImg));
            window.pack();

            //Advance to next every 2 seconds
            try {Thread.sleep(2000);}
            catch (InterruptedException e) {e.printStackTrace();}
        }

        //Processing complete dialog
        JOptionPane.showMessageDialog(null,
                "Image processing complete!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE);

        //TODO Report inspection findings at end
    }

    //Calculate image histogram
    private static int [] hist(Mat imgInput) {

        int hist [] = new int[256];
        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data); //Get all pixels

        for(byte value : data) {
            hist[(value & 0xff)]++;
        }
        return hist;
    }

    //Otsu's Method Global Thresholding
    private static int calculateOtsu(Mat imgInput, int [] histData){

        //Process input image
        byte srcData[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, srcData); //Get all pixels

        //Calculate the histogram
        int ptr =0;
        while(ptr < srcData.length){
            int h = 0xff & srcData[ptr];
            histData[h] ++;
            ptr ++;
        }

        //Total number of pixels
        int total = srcData.length;
        float sum =  0;
        for (int t =0; t < 256; t++){
            sum += t * histData[t];
        }

        float sumB =0;
        int wB = 0;
        int wF;

        float varMax = 0;
        int threshold = 0;

        for(int t = 0; t < 256; t++){
            wB += histData[t];            //Weight of background
            if(wB == 0) continue;

            wF = total - wB;              //Weight of foreground
            if(wF == 0) break;

            sumB += (float) (t * histData[t]);

            float mB = sumB / wB;         //Mean of background
            float mF = (sum - sumB) / wF; //Mean of foreground

            //Calculate between class variance
            float varBetween = (float)wB * (float)wF * (mB - mF) * (mB - mF);

            //Check if new maximum found
            if(varBetween > varMax){
                varMax = varBetween;
                threshold = t;
            }
        }

        return threshold - 50;
    }

    //Process image threshold on input image (convert to binary)
    private static void threshold(Mat imgInput, int t) {

        //Note that we need to use an & with 0xff here.
        //This is because Java uses signed two's complement types.
        //The & operation will give us the pixel in the range we are used to (0..255).

        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data);
        for (int i=0;i<data.length;i++)
        {
            int unsigned = (data[i] & 0xff);
            if (unsigned > t)
                data[i] = (byte)0;
            else
                data[i] = (byte)255;
        }
        imgInput.put(0, 0, data);
    }

    //Dilate the image input
    private static void dilate(Mat imgInput) {

        //Build byte array of input image
        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data); //Get all pixels
        byte copy[] = data.clone(); //Copy of data byte array

        //Loops through 48400 pixels (220x200 images)
        for(int i=0; i<data.length; i++) {

            //Get all 8 neighbour pixels to the current pixel
            int [] neighbours = {i+1, i-1, i-imgInput.cols(), i+imgInput.cols(), i+imgInput.cols()+1, i+imgInput.cols()-1, i-imgInput.cols()+1, i-imgInput.cols()-1};

            try {
                //Loops through all 8 neighbouring pixels
                for(int neighbour : neighbours) {
                    if((copy[neighbour] & 0xff) == 255) {
                        data[i] = (byte) 255;
                    }
                }
            }
            //Ignore ArrayIndexOutOfBounds exceptions
            catch(ArrayIndexOutOfBoundsException ignored) {}
        }

        //Replace imgInput with dilated image data
        imgInput.put(0, 0, data);
    }

    //Erode the image input
    private static void erode(Mat imgInput) {

        //Build byte array of input image
        byte data[] = new byte[imgInput.rows() * imgInput.cols()];
        imgInput.get(0, 0, data); //Get all pixels
        byte copy[] = data.clone(); //Copy of data byte array

        //Loops through 48400 pixels (220x200 images)
        for (int i=0; i<data.length; i++) {

            //Get all 8 neighbour pixels to the current pixel
            int [] neighbours = {i+1, i-1, i-imgInput.cols(), i+imgInput.cols(), i+imgInput.cols()+1, i+imgInput.cols()-1, i-imgInput.cols()+1, i-imgInput.cols()-1};

            try {
                //Loops through all 8 neighbouring pixels
                for(int neighbour : neighbours) {
                    if ((copy[neighbour] & 0xff) == 0) {
                        data[i] = (byte) 0;
                    }
                }
            }
            //Ignore ArrayIndexOutOfBounds exceptions
            catch(ArrayIndexOutOfBoundsException ignored) {}
        }

        //Replace ingInput with eroded image data
        imgInput.put(0, 0, data);
    }

    //Process Connected Component Labelling
    private static void performCCL(Mat imgInput, File file) {

        //1. Perform CCL on imgInput to identify components
        CCL ccl = new CCL();
        imgInput.put(0, 0, ccl.processCCL(imgInput));

        //2. Get new CCL images directory path from base bath
        String filePath = file.getPath();
        String cclDirectoryName = "oring-ccl";
        File cclPath = ccl.getCCLImagesPath(filePath, cclDirectoryName);

        //3. Get image file extension
        String format = ccl.getFileNameExtension(filePath);

        try {
            //4.1 Check if directory exists, if not, create it
            if(ccl.checkIfDirectoryExists(cclPath)) {

                //5. Output fully processed images
                ImageIO.write(Mat2BufferedImage(imgInput), format, new File(cclPath + File.separator + ccl.getBaseFileName(file) + "-ccl" + "." + format));
            }

            //4.2 Throw an IOException if output directory creation fails
            else {
                throw new IOException("Error while creating /" + cclDirectoryName + " directory");
            }
        }
        catch(Exception e) {e.printStackTrace();}
    }

    //Convert to BufferedImage for JLabel
    private static BufferedImage Mat2BufferedImage(Mat m) {

        int type = BufferedImage.TYPE_BYTE_GRAY;
        if (m.channels() > 1) {
            type = BufferedImage.TYPE_3BYTE_BGR;
        }
        int bufferSize = m.channels() * m.cols() * m.rows();
        byte [] b = new byte[bufferSize];

        m.get(0,0,b); //Get all pixels
        BufferedImage image = new BufferedImage(m.cols(), m.rows(), type);

        final byte[] targetPixels = ((DataBufferByte) image.getRaster().getDataBuffer()).getData();
        System.arraycopy(b, 0, targetPixels, 0, b.length);

        return image;
    }

    //Draw image histogram
    private static void drawHistogram(Mat imgInput, int [] hist) {

        //Define histogram scale by finding max hist value
        int max = 0;
        for(int value : hist) {
            if (value > max)
                max = value;
        }
        int scale = max/256;

        //Draw histogram object (incomplete)
        for(int i=0; i<hist.length-1; i++) {
            //Core.circle(imgInput, new Point(i*2+1,imgInput.rows()-(hist[i]/scale)+1), 1, new Scalar(0,255,0));
            Core.line(imgInput, new Point(i+1,imgInput.rows()-(hist[i]/scale)+1), new Point(i+2,imgInput.rows()-(hist[i+1]/scale)+1), new Scalar(0,0,255));
        }
    }

    //Find the largest peak in the histogram
    private static int findHistPeak(int [] hist) {

        int largestValue = hist[0];
        int indexOfLargest = 0;
        for(int i=0; i<hist.length; i++) {
            if(hist[i] > largestValue) {
                largestValue = hist[i];
                indexOfLargest = i;
            }
        }
        return indexOfLargest-50;
    }
}