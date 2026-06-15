// Register only the Chart.js pieces the dashboard uses (tree-shaking friendly). Filler is what
// lets the projection fan shade the area between the p10 and p90 lines.
import {
  CategoryScale,
  Chart,
  Filler,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js';

Chart.register(
  LineController,
  LineElement,
  PointElement,
  LinearScale,
  CategoryScale,
  Filler,
  Tooltip,
  Legend,
);
