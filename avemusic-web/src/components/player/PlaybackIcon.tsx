interface PlaybackIconProps {
    type: "play" | "pause";
    size?: number;
}

export default function PlaybackIcon({
    type,
    size = 16,
}: PlaybackIconProps) {
    return (
        <svg
            width={size}
            height={size}
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            focusable="false"
        >
            {type === "pause" ? (
                <>
                    <rect
                        x="6"
                        y="4.5"
                        width="4.2"
                        height="15"
                        rx="1.4"
                        fill="currentColor"
                    />

                    <rect
                        x="13.8"
                        y="4.5"
                        width="4.2"
                        height="15"
                        rx="1.4"
                        fill="currentColor"
                    />
                </>
            ) : (
                <path
                    d="M8.2 5.4C8.2 4.55 9.15 4.05 9.85 4.52L19.15 10.72C19.78 11.14 19.78 12.07 19.15 12.49L9.85 18.69C9.15 19.16 8.2 18.66 8.2 17.81V5.4Z"
                    fill="currentColor"
                />
            )}
        </svg>
    );
}
