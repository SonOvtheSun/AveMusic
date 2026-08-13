import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { usePlayer } from "../../player/usePlayer";
import PlaybackIcon from "./PlaybackIcon";
import CollectToPlaylistDialog from "./CollectToPlaylistDialog";
import { useAuth } from "../../context/useAuth";

import "../../styles/components/BottomPlayer.css";
import "../../styles/components/BottomPlayer.playlist.css";
import LyricsPanel
    from "./LyricsPanel";

const DEFAULT_COVER =
    "https://images.unsplash.com/photo-1494232410401-ad00d5433cfa?auto=format&fit=crop&w=300&q=80";


function formatTime(
    seconds: number,
): string {
    if (
        !Number.isFinite(seconds)
        || seconds < 0
    ) {
        return "00:00";
    }

    const total =
        Math.floor(seconds);

    const hours =
        Math.floor(total / 3600);

    const minutes =
        Math.floor(
            (total % 3600) / 60,
        );

    const remainingSeconds =
        total % 60;

    if (hours > 0) {
        return [
            hours,
            String(minutes)
                .padStart(2, "0"),
            String(remainingSeconds)
                .padStart(2, "0"),
        ].join(":");
    }

    return [
        String(minutes)
            .padStart(2, "0"),
        String(remainingSeconds)
            .padStart(2, "0"),
    ].join(":");
}

export default function BottomPlayer() {
    const navigate = useNavigate();

    const {
        user,
    } = useAuth();

    const [
        lyricsOpen,
        setLyricsOpen,
    ] = useState(false);

    const [
        collectDialogOpen,
        setCollectDialogOpen,
    ] = useState(false);

    const {
        queue,
        currentSong,
        currentIndex,
        isPlaying,
        currentTime,
        duration,
        volume,
        playlistOpen,
        playbackError,
        playAt,
        togglePlay,
        previous,
        next,
        seek,
        changeVolume,
        togglePlaylist,
    } = usePlayer();

    if (currentSong === null) {
        return null;
    }

    const progressMax =
        Math.max(
            duration,
            currentSong.durationSeconds,
            1,
        );

    const progressValue =
        Math.min(
            currentTime,
            progressMax,
        );

    return (
        <>
            <div
                className="bottom-player-spacer"
                aria-hidden="true"
            />

            <footer className="bottom-player">
                <div className="bottom-player-inner">
                    <div className="player-current-song">
                        <img
                            src={
                                currentSong.coverUrl
                                ?? DEFAULT_COVER
                            }
                            alt={currentSong.name}
                            className="player-cover"
                        />

                        <div className="player-song-text">
                            <strong title={currentSong.name}>
                                {currentSong.name}
                            </strong>

                            {playbackError ? (
                                <span title={playbackError}>
                                    {playbackError}
                                </span>
                            ) : (
                                <div
                                    className="player-artist-links"
                                    title={currentSong.artistName}
                                >
                                    {currentSong.artistName
                                        .split(" / ")
                                        .map(
                                            (
                                                rawName,
                                                index,
                                            ) => {
                                                const name =
                                                    rawName.trim();

                                                const artistId =
                                                    currentSong
                                                        .artistIds[
                                                        index
                                                        ];

                                                return (
                                                    <span
                                                        key={
                                                            artistId
                                                            ?? `${name}-${index}`
                                                        }
                                                        className="player-artist-link-group"
                                                    >
                                                        {index > 0 && (
                                                            <span
                                                                className="player-artist-separator"
                                                                aria-hidden="true"
                                                            >
                                                                {" / "}
                                                            </span>
                                                        )}

                                                        {artistId ? (
                                                            <button
                                                                type="button"
                                                                className="player-artist-link"
                                                                onClick={() =>
                                                                    navigate(
                                                                        `/artists/${artistId}`,
                                                                    )
                                                                }
                                                            >
                                                                {name}
                                                            </button>
                                                        ) : (
                                                            <span>
                                                                {name}
                                                            </span>
                                                        )}
                                                    </span>
                                                );
                                            },
                                        )}
                                </div>
                            )}
                        </div>
                    </div>

                    <div className="player-main-control">
                        <div className="player-buttons">
                            <button
                                type="button"
                                className="player-skip-button"
                                disabled={queue.length <= 1}
                                aria-label="上一首"
                                title="上一首"
                                onClick={previous}
                            >
                                ◀│
                            </button>

                            <button
                                type="button"
                                className="player-play-button"
                                aria-label={
                                    isPlaying
                                        ? "暂停"
                                        : "播放"
                                }
                                title={
                                    isPlaying
                                        ? "暂停"
                                        : "播放"
                                }
                                onClick={togglePlay}
                            >
                                <PlaybackIcon
                                    type={
                                        isPlaying
                                            ? "pause"
                                            : "play"
                                    }
                                    size={17}
                                />
                            </button>

                            <button
                                type="button"
                                className="player-skip-button"
                                disabled={queue.length <= 1}
                                aria-label="下一首"
                                title="下一首"
                                onClick={next}
                            >
                                │▶
                            </button>
                        </div>

                        <div className="player-progress-row">
                            <span>
                                {formatTime(
                                    progressValue,
                                )}
                            </span>

                            <input
                                className="player-progress"
                                type="range"
                                min={0}
                                max={progressMax}
                                step={0.1}
                                value={progressValue}
                                aria-label="播放进度"
                                onChange={(event) =>
                                    seek(
                                        Number(
                                            event.target.value,
                                        ),
                                    )
                                }
                            />

                            <span>
                                {formatTime(
                                    progressMax,
                                )}
                            </span>
                        </div>
                    </div>

                    <div className="player-right-control">
                        <button
                            type="button"
                            className={
                                lyricsOpen
                                    ? "player-lyrics-button active"
                                    : "player-lyrics-button"
                            }
                            aria-label="歌词"
                            title="歌词"
                            onClick={() =>
                                setLyricsOpen(
                                    (current) =>
                                        !current,
                                )
                            }
                        >
                            词
                        </button>

                        <button
                            type="button"
                            className="player-collect-button"
                            aria-label="收藏到歌单"
                            title="收藏到歌单"
                            onClick={() => {
                                if (user === null) {
                                    navigate("/auth");
                                    return;
                                }

                                setCollectDialogOpen(
                                    true,
                                );
                            }}
                        >
                            <span
                                aria-hidden="true"
                                className="player-collect-icon"
                            >
                                ♡
                            </span>


                        </button>

                        <span
                            className="player-volume-icon"
                            aria-hidden="true"
                        >
                            {volume === 0
                                ? "🔇"
                                : "🔊"}
                        </span>

                        <input
                            className="player-volume"
                            type="range"
                            min={0}
                            max={1}
                            step={0.01}
                            value={volume}
                            aria-label="音量"
                            onChange={(event) =>
                                changeVolume(
                                    Number(
                                        event.target.value,
                                    ),
                                )
                            }
                        />

                        <button
                            type="button"
                            className={
                                playlistOpen
                                    ? "player-list-button active"
                                    : "player-list-button"
                            }
                            aria-label="播放列表"
                            aria-expanded={playlistOpen}
                            title="播放列表"
                            onClick={togglePlaylist}
                        >
                            ☷
                            <span>
                                {queue.length}
                            </span>
                        </button>
                    </div>

                    {playlistOpen && (
                        <section
                            className="player-playlist-panel"
                            aria-label="播放列表"
                        >
                            <header>
                                <strong>
                                    播放列表
                                </strong>

                                <span>
                                    {queue.length} 首
                                </span>
                            </header>

                            <div className="player-playlist-scroll">
                                {queue.map(
                                    (song, index) => (
                                        <button
                                            key={song.id}
                                            type="button"
                                            className={
                                                index
                                                === currentIndex
                                                    ? "player-playlist-item active"
                                                    : "player-playlist-item"
                                            }
                                            onClick={() =>
                                                playAt(index)
                                            }
                                        >
                                            <span className="player-playlist-index">
                                                {index + 1}
                                            </span>

                                            <span className="player-playlist-text">
                                                <strong>
                                                    {song.name}
                                                </strong>

                                                <small>
                                                    {song.artistName}
                                                </small>
                                            </span>

                                            {index
                                                === currentIndex
                                                && (
                                                    <span className="player-playlist-state">
                                                        {isPlaying
                                                            ? "播放中"
                                                            : "已暂停"}
                                                    </span>
                                                )}
                                        </button>
                                    ),
                                )}
                            </div>
                        </section>
                    )}
                </div>
            </footer>

            <CollectToPlaylistDialog
                open={collectDialogOpen}
                songId={currentSong.id}
                songName={currentSong.name}
                onClose={() =>
                    setCollectDialogOpen(
                        false,
                    )
                }
            />

            <LyricsPanel
                open={lyricsOpen}
                songId={currentSong.id}
                songName={currentSong.name}
                onClose={() =>
                    setLyricsOpen(false)
                }
            />
        </>
    );
}
