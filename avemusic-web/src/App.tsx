import {
    Navigate,
    Route,
    Routes,
} from "react-router-dom";

import { AuthProvider } from "./context/AuthProvider";
import { PlayerProvider } from "./player/PlayerProvider";

import BottomPlayer from "./components/player/BottomPlayer";

import PlaylistRankingPage
    from "./pages/Playlist/PlaylistRankingPage";

import SearchResultPage
    from "./pages/Search/SearchResultPage";

import ArtistsPage
    from "./pages/Artist/ArtistsPage";

/*
 * 播放器样式在 App 顶层统一导入。
 * 不再依赖 BottomPlayer.tsx 自己加载 CSS。
 */
import "./styles/components/BottomPlayer.css";

import LoginPage from "./pages/Auth/LoginPage";
import HomePage from "./pages/Home/HomePage";
import ArtistPage from "./pages/Artist/ArtistPage";
import AlbumPage from "./pages/Album/AlbumPage";
import ManagementPage from "./pages/Management/ManagementPage";
import MyMusicPage from "./pages/MyMusic/MyMusicPage";
import PlaylistDetailPage from "./pages/Playlist/PlaylistDetailPage";

export default function App() {
    return (
        <AuthProvider>
            <PlayerProvider>
                <Routes>
                    <Route
                        path="/"
                        element={<HomePage />}
                    />

                    <Route
                        path="/auth"
                        element={<LoginPage />}
                    />

                    <Route
                        path="/artists/:id"
                        element={<ArtistPage />}
                    />

                    <Route
                        path="/artists"
                        element={<ArtistsPage />}
                    />

                    <Route
                        path="/albums/:id"
                        element={<AlbumPage />}
                    />

                    <Route
                        path="/search"
                        element={
                            <SearchResultPage />
                        }
                    />

                    <Route
                        path="/management"
                        element={<ManagementPage />}
                    />

                    <Route
                        path="/my-music"
                        element={<MyMusicPage />}
                    />

                    <Route
                        path="/playlists/:playlistId"
                        element={<PlaylistDetailPage />}
                    />

                    <Route
                        path="/discover/playlists/:playlistId"
                        element={
                            <PlaylistDetailPage />
                        }
                    />

                    <Route
                        path="/discover/playlists"
                        element={
                            <PlaylistRankingPage />
                        }
                    />

                    <Route
                        path="*"
                        element={
                            <Navigate
                                to="/"
                                replace
                            />
                        }
                    />
                </Routes>

                <BottomPlayer />
            </PlayerProvider>
        </AuthProvider>
    );
}
