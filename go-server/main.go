package main

import (
	"encoding/json"
	sw "example.com/go-serve/go"
	"github.com/gorilla/mux"
	"log"
	"net/http"
	"strconv"
)

var albumsService = sw.NewAlbumsService()

func main() {
	log.Printf("Server started")

	r := mux.NewRouter()

	r.HandleFunc("/albums/{albumId}", GetAlbumByKeyHandler).Methods("GET")
	r.HandleFunc("/albums", NewAlbumHandler).Methods("POST")

	http.Handle("/", r)
	log.Fatal(http.ListenAndServe(":8080", nil))
}

func GetAlbumByKeyHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	vars := mux.Vars(r)
	albumId, ok := vars["albumId"]
	if !ok {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(sw.ErrorMsg{Msg: "Invalid request"})
		return
	}

	album, found := albumsService.GetAlbumByKey(albumId)
	if !found {
		w.WriteHeader(http.StatusNotFound)
		json.NewEncoder(w).Encode(sw.ErrorMsg{Msg: "Album not found"})
		return
	}

	json.NewEncoder(w).Encode(album)
}

func NewAlbumHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("Content-Type", "application/json; charset=UTF-8")

	image, _, err := r.FormFile("image")
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(sw.ErrorMsg{Msg: "Missing image data"})
		return
	}
	defer image.Close()

	profileData := r.FormValue("profile")
	if profileData == "" {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(sw.ErrorMsg{Msg: "Missing profile data"})
		return
	}

	var profile sw.AlbumsProfile
	err = json.Unmarshal([]byte(profileData), &profile)
	if err != nil {
		w.WriteHeader(http.StatusBadRequest)
		json.NewEncoder(w).Encode(sw.ErrorMsg{Msg: "Invalid profile data"})
		return
	}

	albumId := albumsService.PostNewAlbum(sw.AlbumInfo{
		Artist: profile.Artist,
		Title:  profile.Title,
		Year:   profile.Year,
	})

	fileSize, _ := image.Seek(0, 2) // Seek to end to get the size
	image.Seek(0, 0)                // Seek back to start
	imageMetaData := sw.ImageMetaData{
		AlbumID:   albumId,
		ImageSize: strconv.Itoa(int(fileSize)),
	}

	json.NewEncoder(w).Encode(imageMetaData)
}
