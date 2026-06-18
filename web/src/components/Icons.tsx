import type { SVGProps } from "react";

type IconProps = SVGProps<SVGSVGElement> & { title?: string };

export function IconHeart(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M12 20.3 10.6 19C5.4 14.2 2 11.1 2 7.3 2 4.2 4.4 2 7.4 2c1.7 0 3.4.8 4.6 2.1A6.1 6.1 0 0 1 16.6 2C19.6 2 22 4.2 22 7.3c0 3.8-3.4 6.9-8.6 11.7L12 20.3Z" />
    </svg>
  );
}

export function IconComment(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M12 3C6.5 3 2 6.7 2 11.4c0 2.7 1.5 5.1 3.9 6.6l-.8 2.9c-.2.8.6 1.4 1.3 1l3.6-2c.7.1 1.3.2 2 .2 5.5 0 10-3.7 10-8.4S17.5 3 12 3Zm-4.2 8.2h8.4v1.8H7.8v-1.8Zm0-3.2h8.4v1.8H7.8V8Z" />
    </svg>
  );
}

export function IconUpload(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M5 19h14v-2H5v2Zm6-4h2V8.8l2.5 2.5 1.4-1.4L12 5 7.1 9.9l1.4 1.4L11 8.8V15Z" />
    </svg>
  );
}

export function IconSearch(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M10.8 4a6.8 6.8 0 0 1 5.4 10.9l3.4 3.4-1.4 1.4-3.4-3.4A6.8 6.8 0 1 1 10.8 4Zm0 2a4.8 4.8 0 1 0 0 9.6 4.8 4.8 0 0 0 0-9.6Z" />
    </svg>
  );
}

export function IconUser(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M12 12a4.5 4.5 0 1 0 0-9 4.5 4.5 0 0 0 0 9Zm0 2c-4.4 0-8 2.2-8 5v2h16v-2c0-2.8-3.6-5-8-5Z" />
    </svg>
  );
}

export function IconChevronUp(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="m12 8 7 7-1.4 1.4L12 10.8l-5.6 5.6L5 15l7-7Z" />
    </svg>
  );
}

export function IconChevronDown(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="m12 16-7-7 1.4-1.4 5.6 5.6 5.6-5.6L19 9l-7 7Z" />
    </svg>
  );
}

export function IconRefresh(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M17.7 6.3A8 8 0 0 0 4 12h-2a10 10 0 0 1 17.1-7.1L21 3v6h-6l2.7-2.7ZM6.3 17.7A8 8 0 0 0 20 12h2A10 10 0 0 1 4.9 19.1L3 21v-6h6l-2.7 2.7Z" />
    </svg>
  );
}

export function IconPlay(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M8 5v14l11-7L8 5Z" />
    </svg>
  );
}

export function IconTrash(props: IconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true" {...props}>
      <path d="M9 3h6l1 2h4v2H4V5h4l1-2Zm-2 6h10l-.7 12H7.7L7 9Zm3 2 .3 8h1.6l-.3-8H10Zm4 0-.3 8h1.6l.3-8H14Z" />
    </svg>
  );
}
