import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Icon } from './icons';

interface Props {
  children: ReactNode;
}

interface State {
  error: Error | null;
}

/**
 * Catches render-time errors anywhere in the tree so a single broken component shows a recoverable
 * message instead of a blank white page. The full error is logged to the console for diagnosis;
 * nothing about it is shown to the visitor beyond a friendly explanation.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('ReLoop UI crashed', error, info.componentStack);
  }

  private reset = () => {
    this.setState({ error: null });
  };

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <div className="page" style={{ maxWidth: 640, margin: '10vh auto' }}>
        <div className="card">
          <div className="card-head">
            <span className="icon-tile" aria-hidden="true">
              <Icon name="alert" size={17} />
            </span>
            <h2>Something went wrong</h2>
          </div>
          <p className="muted small" style={{ marginBottom: 18 }}>
            This part of ReLoop hit an unexpected error. Your data is safe — reload the page, or go
            back to your dashboard and try again.
          </p>
          <div className="btn-row">
            <button type="button" className="btn" onClick={() => window.location.reload()}>
              Reload the page
            </button>
            <button
              type="button"
              className="btn secondary"
              onClick={() => {
                this.reset();
                window.location.assign('/dashboard');
              }}
            >
              Back to dashboard
            </button>
          </div>
        </div>
      </div>
    );
  }
}
