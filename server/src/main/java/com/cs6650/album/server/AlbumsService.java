package com.cs6650.album.server;




import com.cs6650.album.server.bean.AlbumInfo;
import com.cs6650.album.server.bean.ImageMetaData;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class AlbumsService {
    private ConcurrentHashMap <String, AlbumInfo> albumInfos = new ConcurrentHashMap<>();

    AtomicInteger albumId = new AtomicInteger(1);


    public AlbumInfo doGetAlbumByKey(String albumId) throws IOException {
        AlbumInfo album = albumInfos.get(albumId);
        if (album == null) {
            throw new IOException("Album not found for the given id: " + albumId);
        }
        return album;
    }


    public String doPostNewAlbum(AlbumInfo albumInfo) throws IOException {
        String newAlbumId = String.valueOf(albumId.getAndIncrement());
//        System.out.println("New album id: " + newAlbumId);
        albumInfos.put(newAlbumId, albumInfo);
        return newAlbumId;
    }

}

