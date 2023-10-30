package com.cs6650.album.server;




import com.cs6650.album.server.bean.AlbumInfo;
import com.cs6650.album.server.bean.ErrorMessage;
import com.cs6650.album.server.bean.ImageMetaData;
import com.google.gson.Gson;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Scanner;
import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

@WebServlet(name = "AlbumServlet", value = "/albums")
@MultipartConfig
public class AlbumsServlet extends HttpServlet {

    Gson gson = new Gson();
    private static AlbumsService albumsService = new AlbumsService();



    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        res.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");
        System.out.println(req.getPathInfo());
        String albumId = req.getPathInfo().substring(1);// Remove the leading '/'
        if (albumId.isEmpty()) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            ErrorMessage errorMsg = new ErrorMessage("Invalid request");
            res.getWriter().write(gson.toJson(errorMsg));
            return;
        }

        // Fetch the album by its ID
        try {
            AlbumInfo album = albumsService.doGetAlbumByKey(albumId);
            String responseStr = gson.toJson(album);
            PrintWriter out = res.getWriter();
            out.print(responseStr);
            out.flush();
        } catch (IOException e) {
            res.setStatus(HttpServletResponse.SC_NOT_FOUND);
            ErrorMessage errorMsg = new ErrorMessage("Album not found");
            res.getWriter().write(gson.toJson(errorMsg));
        }

    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        res.setContentType("application/json");
        req.setCharacterEncoding("UTF-8");

        // 1. Check that we are at the right URL endpoint
        if (!req.getServletPath().equals("/albums")) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            res.getWriter().write("invalid url path");
            return;
        }

        // 2. Handle the multipart/form-data for image and profile
        Part imagePart = req.getPart("image");
        Part profilePart = req.getPart("profile");

        if (imagePart == null || profilePart == null) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            ErrorMessage errorMsg = new ErrorMessage("Missing image or profile data");
            res.getWriter().write(gson.toJson(errorMsg));
            return;
        }

        long imageSize = imagePart.getSize();
        // Check the image type
        String contentType = imagePart.getContentType();
        if (!contentType.equalsIgnoreCase("image/jpeg") &&
                !contentType.equalsIgnoreCase("image/png") &&
                !contentType.equalsIgnoreCase("image/gif")) {
            res.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            ErrorMessage errorMsg = new ErrorMessage("Invalid image type");
            res.getWriter().write(gson.toJson(errorMsg));
            return;
        }

        // Read profile and parse it
        Scanner scanner = new Scanner(profilePart.getInputStream()).useDelimiter("\\A");
        String profileData = scanner.hasNext() ? scanner.next() : "";
        AlbumInfo profile = gson.fromJson(profileData, AlbumInfo.class);

        // Store the album profile
        String albumId = albumsService.doPostNewAlbum(profile);

        ImageMetaData imagedata = new ImageMetaData(albumId, String.valueOf(imageSize));
        String responseStr = gson.toJson(imagedata);
        PrintWriter out = res.getWriter();
        out.print(responseStr);
        out.flush();
    }
}
