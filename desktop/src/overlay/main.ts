import { mount } from 'svelte'
import { installErrorReporting } from '../lib/diagnostics'
import '../styles/base.css'
import Overlay from './Overlay.svelte'

installErrorReporting()
mount(Overlay, { target: document.getElementById('app')! })
